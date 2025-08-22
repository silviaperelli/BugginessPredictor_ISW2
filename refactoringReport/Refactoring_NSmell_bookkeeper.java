//bookkeeper-server/src/main/java/org/apache/bookkeeper/bookie/EntryLogger.java/readEntry(long, long, long)
//Buggy method from last release of the dataset (release ID --> 5 / release 4.2.2) with Max NumSmells
//Number of Code Smell: 3
//    Complessità Ciclomatica > 7 (Smell #1)
//    Lines of Code (LOC) > 30 (Smell #2)
//    Magic Numbers > 1 (Smell #3)

byte[] readEntry(long ledgerId, long entryId, long location) throws IOException, Bookie.NoEntryException {
    long entryLogId = location >> 32L;
    long pos = location & 0xffffffffL;
    ByteBuffer sizeBuff = ByteBuffer.allocate(4);
    pos -= 4; // we want to get the ledgerId and length to check
    BufferedChannel fc;
    try {
        fc = getChannelForLogId(entryLogId);
    } catch (FileNotFoundException e) {
        FileNotFoundException newe = new FileNotFoundException(e.getMessage() + " for " + ledgerId + " with location " + location);
        newe.setStackTrace(e.getStackTrace());
        throw newe;
    }
    if (fc.read(sizeBuff, pos) != sizeBuff.capacity()) {
        throw new Bookie.NoEntryException("Short read from entrylog " + entryLogId,
                ledgerId, entryId);
    }
    pos += 4;
    sizeBuff.flip();
    int entrySize = sizeBuff.getInt();
    // entrySize does not include the ledgerId
    if (entrySize > MB) {
        LOG.error("Sanity check failed for entry size of " + entrySize + " at location " + pos + " in " + entryLogId);

    }
    if (entrySize < MIN_SANE_ENTRY_SIZE) {
        LOG.error("Read invalid entry length {}", entrySize);
        throw new IOException("Invalid entry length " + entrySize);
    }
    byte data[] = new byte[entrySize];
    ByteBuffer buff = ByteBuffer.wrap(data);
    int rc = fc.read(buff, pos);
    if ( rc != data.length) {
        // Note that throwing NoEntryException here instead of IOException is not
        // without risk. If all bookies in a quorum throw this same exception
        // the client will assume that it has reached the end of the ledger.
        // However, this may not be the case, as a very specific error condition
        // could have occurred, where the length of the entry was corrupted on all
        // replicas. However, the chance of this happening is very very low, so
        // returning NoEntryException is mostly safe.
        throw new Bookie.NoEntryException("Short read for " + ledgerId + "@"
                + entryId + " in " + entryLogId + "@"
                + pos + "("+rc+"!="+data.length+")", ledgerId, entryId);
    }
    buff.flip();
    long thisLedgerId = buff.getLong();
    if (thisLedgerId != ledgerId) {
        throw new IOException("problem found in " + entryLogId + "@" + entryId + " at position + " + pos + " entry belongs to " + thisLedgerId + " not " + ledgerId);
    }
    long thisEntryId = buff.getLong();
    if (thisEntryId != entryId) {
        throw new IOException("problem found in " + entryLogId + "@" + entryId + " at position + " + pos + " entry is " + thisEntryId + " not " + entryId);
    }

    return data;
}

// REFACTOR

// === COSTANTI PER ELIMINARE MAGIC NUMBERS ===
private static final int METADATA_LENGTH_BYTES = 4;
private static final long POSITION_MASK = 0xffffffffL;
private static final int LEDGERID_SHIFT_BITS = 32;

byte[] readEntry2(long ledgerId, long entryId, long location) 
        throws IOException, Bookie.NoEntryException {

    long entryLogId = location >> LEDGERID_SHIFT_BITS;
    long pos = (location & POSITION_MASK) - METADATA_LENGTH_BYTES;

    BufferedChannel fc = getChannelForLog(entryLogId, ledgerId, location);

    int entrySize = readEntrySize(fc, pos, ledgerId, entryId);
    validateEntrySize(entrySize, entryLogId, pos);

    byte[] data = new byte[entrySize];
    ByteBuffer buff = ByteBuffer.wrap(data);
    int rc = fc.read(buff, pos + METADATA_LENGTH_BYTES);

    if (rc != data.length) {
        throw new Bookie.NoEntryException(
            String.format("Short read for %d@%d in %d@%d (%d != %d)",
                          ledgerId, entryId, entryLogId, pos, rc, data.length),
            ledgerId, entryId);
    }

    validateHeader(data, ledgerId, entryId, entryLogId, pos);
    return data;
}

// --- METODI ESTRATTI "MOLTO MIRATI" ---

private BufferedChannel getChannelForLog(long entryLogId, long ledgerId, long location) throws IOException {
    try {
        return getChannelForLogId(entryLogId);
    } catch (FileNotFoundException e) {
        FileNotFoundException newe = new FileNotFoundException(
            String.format("%s for %d with location %d", e.getMessage(), ledgerId, location));
        newe.setStackTrace(e.getStackTrace()); // preserva lo stacktrace originale
        throw newe;
    }
}

private int readEntrySize(BufferedChannel fc, long pos, long ledgerId, long entryId) throws IOException {
    ByteBuffer sizeBuff = ByteBuffer.allocate(METADATA_LENGTH_BYTES);
    if (fc.read(sizeBuff, pos) != METADATA_LENGTH_BYTES) {
        throw new Bookie.NoEntryException(
            "Short read from entrylog " + fc.getLogId(), ledgerId, entryId);
    }
    sizeBuff.flip();
    return sizeBuff.getInt();
}

private void validateEntrySize(int entrySize, long entryLogId, long pos) throws IOException {
    if (entrySize > MB) {
        LOG.error("Sanity check failed for entry size of {} at location {} in {}",
                  entrySize, pos, entryLogId);
    }
    // Usa la stessa soglia dell'originale
    if (entrySize < MIN_SANE_ENTRY_SIZE) {
        LOG.error("Read invalid entry length {}", entrySize);
        throw new IOException("Invalid entry length " + entrySize);
    }
}

private void validateHeader(byte[] data, long expectedLedgerId, long expectedEntryId,
                             long entryLogId, long pos) throws IOException {
    ByteBuffer buff = ByteBuffer.wrap(data);
    long actualLedgerId = buff.getLong();
    if (actualLedgerId != expectedLedgerId) {
        throw new IOException(
            String.format("problem found in %d@%d entry belongs to %d not %d",
                          entryLogId, pos, actualLedgerId, expectedLedgerId));
    }
    long actualEntryId = buff.getLong();
    if (actualEntryId != expectedEntryId) {
         throw new IOException(
            String.format("problem found in %d@%d entry is %d not %d",
                          entryLogId, pos, actualEntryId, expectedEntryId));
    }
}
