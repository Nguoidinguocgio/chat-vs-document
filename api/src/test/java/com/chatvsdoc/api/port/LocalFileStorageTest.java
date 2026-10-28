package com.chatvsdoc.api.port;

import com.chatvsdoc.api.port.adapter.LocalFileStorage;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

/** Bản giả cho test của StoragePort = LocalFileStorage trên thư mục tạm. */
class LocalFileStorageTest extends StoragePortContract {

    @TempDir Path dir;

    @Override
    protected StoragePort create() throws Exception {
        return new LocalFileStorage(dir);
    }
}
