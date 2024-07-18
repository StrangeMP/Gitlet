package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.List;

public class StageObject implements Serializable {
    File file;
    Blob blob;
    boolean newBlob = false;

    private StageObject(File f, Blob b) {
        file = f;
        blob = b;
    }

    private StageObject(File f, Blob b, Boolean nb) {
        this(f, b);
        newBlob = nb;
    }

    /**
     * @param f File being staged.
     * @return a StageObject tracking the staged file, null if no need to stage.
     */
    static StageObject of(File f) {
        List<Blob> blob = Repository.getBlobs().get(f);
        String sha1 = Repository.sha1OfFile(f);

        // completely new file
        if (blob == null) {
            return new StageObject(f, new Blob(f), true);
        }

        // check if the staging file is identical with the one in _head
        Blob relativeBlob = Repository.getHead().content.get(f);
        if (relativeBlob != null && relativeBlob.sha1.equals(sha1)) {
            return null;
        }

        // check if there is an existing version identical to the staging file
        for (Blob b : blob) {
            if (b.sha1.equals(sha1)) {
                return new StageObject(f, b);
            }
        }

        // file edited
        return new StageObject(f, new Blob(f), true);
    }

    public void clear() {
        if (newBlob) {
            try {
                Files.delete(blob.underlying.toPath());
            } catch (IOException ignored) {
                System.err.println("IOException");
            }
        }
    }

}
