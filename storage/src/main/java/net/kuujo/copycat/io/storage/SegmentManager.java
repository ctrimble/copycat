/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.io.storage;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.FileBuffer;
import net.kuujo.copycat.io.HeapBuffer;
import net.kuujo.copycat.io.serializer.Serializer;
import net.kuujo.copycat.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Log segment manager.
 * <p>
 * The segment manager keeps track of segments in a given {@link Log} and provides an interface to loading, retrieving,
 * and compacting those segments.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
class SegmentManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentManager.class);
  private final String name;
  private final Storage storage;
  private final NavigableMap<Long, Segment> segments = new ConcurrentSkipListMap<>();
  private Segment currentSegment;
  private volatile long commitIndex;

  /**
   * @throws NullPointerException if {@code segments} is null
   */
  public SegmentManager(String name, Storage storage) {
    this.name = Assert.notNull(name, "name");
    this.storage = Assert.notNull(storage, "storage");
    open();
  }

  /**
   * Returns the entry serializer.
   *
   * @return The entry serializer.
   */
  public Serializer serializer() {
    return storage.serializer();
  }

  /**
   * Sets the log commit index.
   *
   * @param index The log commit index.
   * @return The segment manager.
   */
  SegmentManager commitIndex(long index) {
    this.commitIndex = Math.max(this.commitIndex, index);
    return this;
  }

  /**
   * Returns the log commit index.
   *
   * @return The log commit index.
   */
  public long commitIndex() {
    return commitIndex;
  }

  /**
   * Opens the segments.
   */
  private void open() {
    // Load existing log segments from disk.
    for (Segment segment : loadSegments()) {
      segments.put(segment.descriptor().index(), segment);
    }

    // If a segment doesn't already exist, create an initial segment starting at index 1.
    if (!segments.isEmpty()) {
      currentSegment = segments.lastEntry().getValue();
    } else {
      SegmentDescriptor descriptor = SegmentDescriptor.builder()
        .withId(1)
        .withVersion(1)
        .withIndex(1)
        .withMaxEntrySize(storage.maxEntrySize())
        .withMaxSegmentSize(storage.maxSegmentSize())
        .withMaxEntries(storage.maxEntriesPerSegment())
        .build();

      descriptor.lock();

      currentSegment = createSegment(descriptor);
      currentSegment.descriptor().update(System.currentTimeMillis());
      currentSegment.descriptor().lock();

      segments.put(1l, currentSegment);
    }
  }

  /**
   * Asserts that the manager is open.
   * 
   * @throws IllegalStateException if the segment manager is not open
   */
  private void assertOpen() {
    Assert.state(currentSegment != null, "segment manager not open");
  }

  /**
   * Returns the current segment.
   *
   * @return The current segment.
   */
  public Segment currentSegment() {
    return currentSegment != null ? currentSegment : lastSegment();
  }

  /**
   * Resets the current segment, creating a new segment if necessary.
   */
  private void resetCurrentSegment() {
    Segment lastSegment = lastSegment();
    if (lastSegment != null) {
      currentSegment = lastSegment;
    } else {
      SegmentDescriptor descriptor = SegmentDescriptor.builder()
        .withId(1)
        .withVersion(1)
        .withIndex(1)
        .withMaxEntrySize(storage.maxEntrySize())
        .withMaxSegmentSize(storage.maxSegmentSize())
        .withMaxEntries(storage.maxEntriesPerSegment())
        .build();
      descriptor.lock();

      currentSegment = createSegment(descriptor);

      segments.put(1L, currentSegment);
    }
  }

  /**
   * Returns the first segment in the log.
   * 
   * @throws IllegalStateException if the segment manager is not open
   */
  public Segment firstSegment() {
    assertOpen();
    Map.Entry<Long, Segment> segment = segments.firstEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Returns the last segment in the log.
   * 
   * @throws IllegalStateException if the segment manager is not open
   */
  public Segment lastSegment() {
    assertOpen();
    Map.Entry<Long, Segment> segment = segments.lastEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Creates and returns the next segment.
   *
   * @return The next segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  public Segment nextSegment() {
    assertOpen();
    Segment lastSegment = lastSegment();
    SegmentDescriptor descriptor = SegmentDescriptor.builder()
      .withId(lastSegment != null ? lastSegment.descriptor().id() + 1 : 1)
      .withVersion(1)
      .withIndex(currentSegment.lastIndex() + 1)
      .withMaxEntrySize(storage.maxEntrySize())
      .withMaxSegmentSize(storage.maxSegmentSize())
      .withMaxEntries(storage.maxEntriesPerSegment())
      .build();
    descriptor.lock();

    currentSegment = createSegment(descriptor);

    segments.put(descriptor.index(), currentSegment);
    return currentSegment;
  }

  /**
   * Returns the collection of segments.
   *
   * @return An ordered collection of segments.
   */
  public Collection<Segment> segments() {
    return segments.values();
  }

  /**
   * Returns the segment for the given index.
   *
   * @param index The index for which to return the segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  public Segment segment(long index) {
    assertOpen();
    // Check if the current segment contains the given index first in order to prevent an unnecessary map lookup.
    if (currentSegment != null && currentSegment.validIndex(index))
      return currentSegment;

    // If the index is in another segment, get the entry with the next lowest first index.
    Map.Entry<Long, Segment> segment = segments.floorEntry(index);
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Inserts a segment.
   *
   * @param segment The segment to insert.
   * @throws IllegalStateException if the segment is unknown
   */
  public synchronized void insertSegment(Segment segment) {
    Segment oldSegment = segments.put(segment.index(), segment);
    Assert.arg(oldSegment != null, "unknown segment at index: %s", segment.index());
    segments.put(oldSegment.index(), oldSegment);
  }

  /**
   * Removes a segment.
   *
   * @param segment The segment to remove.
   */
  public synchronized void removeSegment(Segment segment) {
    segments.remove(segment.index());
    resetCurrentSegment();
  }

  /**
   * Moved a segment.
   *
   * @param index The index to move.
   * @param segment The segment to move.
   */
  synchronized void moveSegment(long index, Segment segment) {
    segments.remove(index);
    if (!segment.isEmpty())
      segments.put(segment.index(), segment);
  }

  /**
   * Create a new segment.
   */
  public Segment createSegment(SegmentDescriptor descriptor) {
    File segmentFile = SegmentFile.createSegmentFile(name, storage.directory(), descriptor.id(), descriptor.version());
    Buffer buffer = FileBuffer.allocate(segmentFile, 1024 * 1024, descriptor.maxSegmentSize() + SegmentDescriptor.BYTES);
    descriptor.copyTo(buffer);
    Segment segment = new Segment(buffer.position(SegmentDescriptor.BYTES).slice(), descriptor, createIndex(descriptor), storage.serializer().clone(), this);
    LOGGER.debug("Created segment: {}", segment);
    return segment;
  }

  /**
   * Loads a segment.
   */
  public Segment loadSegment(long segmentId, long segmentVersion) {
    File file = SegmentFile.createSegmentFile(name, storage.directory(), segmentId, segmentVersion);
    Buffer buffer = FileBuffer.allocate(file, Math.min(1024 * 1024, storage.maxSegmentSize() + storage.maxEntrySize() + SegmentDescriptor.BYTES), storage.maxSegmentSize() + storage.maxEntrySize() + SegmentDescriptor.BYTES);
    SegmentDescriptor descriptor = new SegmentDescriptor(buffer);
    Segment segment = new Segment(buffer.position(SegmentDescriptor.BYTES).slice(), descriptor, createIndex(descriptor), storage.serializer().clone(), this);
    LOGGER.debug("Loaded segment: {} ({})", descriptor.id(), file.getName());
    return segment;
  }

  /**
   * Creates an in memory segment index.
   */
  private OffsetIndex createIndex(SegmentDescriptor descriptor) {
    return new OffsetIndex(HeapBuffer.allocate(Math.min(1024 * 1024, descriptor.maxEntries()), OffsetIndex.size(descriptor.maxEntries())));
  }

  /**
   * Loads all segments from disk.
   *
   * @return A collection of segments for the log.
   */
  protected Collection<Segment> loadSegments() {
    // Ensure log directories are created.
    storage.directory().mkdirs();

    TreeMap<Long, Segment> segments = new TreeMap<>();

    // Iterate through all files in the log directory.
    for (File file : storage.directory().listFiles(File::isFile)) {

      // If the file looks like a segment file, attempt to load the segment.
      if (SegmentFile.isSegmentFile(name, file)) {
        SegmentFile segmentFile = new SegmentFile(file);
        SegmentDescriptor descriptor = new SegmentDescriptor(FileBuffer.allocate(file, SegmentDescriptor.BYTES));

        // Valid segments will have been locked. Segments that resulting from failures during log cleaning will be
        // unlocked and should ultimately be deleted from disk.
        if (descriptor.locked()) {

          // Load the segment.
          Segment segment = loadSegment(descriptor.id(), descriptor.version());

          // Check whether an existing segment with the segment index already exists. Note that we don't use segment IDs
          // since the log compaction process combines segments and therefore it's not easy to determine which segment
          // will contain a given starting index by ID.
          Map.Entry<Long, Segment> existingEntry = segments.floorEntry(segment.descriptor().index());
          if (existingEntry != null) {

            // If an existing descriptor exists with a lower index than this segment's first index, check to determine
            // whether this segment's first index is contained in that existing index. If it is, determine which segment
            // should take precedence based on segment versions.
            Segment existingSegment = existingEntry.getValue();
            if (existingSegment.firstIndex() <= segment.firstIndex() && existingSegment.firstIndex() + existingSegment.length() > segment.firstIndex()) {
              if (existingSegment.descriptor().version() < segment.descriptor().version()) {
                LOGGER.debug("Replaced segment {} with newer version: {} ({})", existingSegment.descriptor().id(), segment.descriptor().version(), segmentFile.file().getName());
                segments.remove(existingEntry.getKey());
                existingSegment.close();
                existingSegment.delete();
                segments.put(segment.firstIndex(), segment);
              } else {
                segment.close();
                segment.delete();
              }
            }
            // If the next closes existing segment didn't contain this segment's first index, add this segment.
            else {
              LOGGER.debug("Found segment: {} ({})", segment.descriptor().id(), segmentFile.file().getName());
              segments.put(segment.firstIndex(), segment);
            }
          }
          // If there was no segment with a starting index close to this segment's index, add this segment.
          else {
            LOGGER.debug("Found segment: {} ({})", segment.descriptor().id(), segmentFile.file().getName());
            segments.put(segment.firstIndex(), segment);
          }

          descriptor.close();
        }
        // If the segment descriptor wasn't locked, close and delete the descriptor.
        else {
          descriptor.close();
          descriptor.delete();
        }
      }
    }

    for (Long segmentId : new HashSet<>(segments.keySet())) {
      Segment segment = segments.get(segmentId);
      Map.Entry<Long, Segment> previousEntry = segments.floorEntry(segmentId - 1);
      if (previousEntry != null) {
        Segment previousSegment = previousEntry.getValue();
        if (previousSegment.index() + previousSegment.length() + 1 < segment.index()) {
          previousSegment.skip(segment.index() - (previousSegment.index() + previousSegment.length()));
        }
      }
    }

    return segments.values();
  }

  @Override
  public void close() {
    segments.values().forEach(s -> {
      LOGGER.debug("Closing segment: {}", s.descriptor().id());
      s.close();
    });
    segments.clear();
    currentSegment = null;
  }

  /**
   * Deletes all segments.
   */
  public void delete() {
    loadSegments().forEach(s -> {
      LOGGER.debug("Deleting segment: {}", s.descriptor().id());
      s.delete();
    });
  }

  @Override
  public String toString() {
    return String.format("%s[directory=%s, segments=%d]", getClass().getSimpleName(), storage.directory(), segments.size());
  }

}
