package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Blob implements Serializable {
    File origin;
    File underlying;
    String sha1;

    Blob(File f) {
        origin = f;
        sha1 = Repository.sha1OfFile(f);
        underlying = Utils.join(Repository.OBJECT_DIR, sha1);
        Repository.copyFile(f, underlying);
        List<Blob> listOfBlobs = Repository.getBlobs().get(f);
        if (listOfBlobs != null) {
            listOfBlobs.add(this);
        } else {
            ArrayList<Blob> al = new ArrayList<>();
            al.add(this);
            Repository.getBlobs().put(f, al);
        }
    }

    public void checkout() {
        Repository.copyFile(this.underlying, this.origin);
    }

}
