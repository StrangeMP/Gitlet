package gitlet;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.Utils.*;

/**
 * Represents a gitlet repository.
 *
 * @author StrangeMP
 */
public class Repository implements Serializable {
    private static Repository me;
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    public static final File OBJECT_DIR = join(GITLET_DIR, "objects");
    private static final File LOG_DIR = join(GITLET_DIR, "logs");
    private static final File BRANCH_LOG_DIR = join(LOG_DIR, "refs", "heads");
    private static final File CORE_FILE = join(OBJECT_DIR, "CORE");
    private static final File HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File HEAD_LOG_FILE = join(LOG_DIR, "HEAD");
    private static final File GLOBAL_LOG_FILE = join(LOG_DIR, "GLOBAL");

    /**
     * Mapping branch name to head of branches.
     */
    TreeMap<String, Commit> branches;
    Commit _head;
    String currentBranchName;

    /**
     * Mapping File to its corresponding versions of blobs.
     */
    HashMap<File, List<Blob>> blobs;

    /**
     * Mapping staged file to their corresponding StageObject.
     */
    static class StageType extends HashMap<File, StageObject> {
        StageType() {
            super();
        }

        @Override
        public void clear() {
            for (StageObject so : super.values()) {
                if (so != null) {
                    so.clear();
                }
            }
            super.clear();
        }
    }

    StageType stage;

    /**
     * Mapping sha1 to Commits
     */
    Map<String, Commit> commits;

    public static void copyFile(File from, File to) {
        try {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            Main.exit("IOException");
        }
    }

    /**
     * Constructor used for repo initialization.
     */
    private Repository() {
        me = this;
        branches = new TreeMap<>();
        blobs = new HashMap<>();
        stage = new StageType();
        Date date = new Date();
        currentBranchName = "master";
        commits = new HashMap<>();
        try {
            GITLET_DIR.mkdir();
            OBJECT_DIR.mkdir();
            LOG_DIR.mkdir();
            BRANCH_LOG_DIR.mkdirs();
            HEAD_FILE.createNewFile();
            HEAD_LOG_FILE.createNewFile();
            GLOBAL_LOG_FILE.createNewFile();
            File masterLog = Utils.join(BRANCH_LOG_DIR, "master");
            masterLog.createNewFile();
            commit("initial commit");
            Utils.writeContents(HEAD_FILE, _head.sha1);
        } catch (IOException ignored) {
            System.exit(0);
        }
    }

    static Commit getHead() {
        return me._head;
    }

    static HashMap<File, List<Blob>> getBlobs() {
        return me.blobs;
    }

    static HashMap<File, StageObject> getStage() {
        return me.stage;
    }

    public static Repository init() {
        if (GITLET_DIR.exists()) {
            Main.exit(
                    "A Gitlet version-control system already exists in the current directory."
            );
        }
        return new Repository();
    }

    /**
     * Stage a file if it is untracked or modified.
     * If the file has been staged previously and not modified since its
     * last staging, the method does nothing, otherwise it is re-staged with the newest content.
     *
     * @param fileName filename of the file to be staged.
     */
    public void add(String fileName) {
        File stagingFile = join(CWD, fileName);
        if (!stagingFile.exists()) {
            Main.exit("File does not exist.");
        }

        // the file being staged has been staged previously...
        if (stage.containsKey(stagingFile)) {
            // ...but is marked for removal and is identical with the version in head,
            // just unstage the file
            if (stage.get(stagingFile) == null) {
                stage.remove(stagingFile);
                return;
            }
            // ...and is identical with the version just staged.
            if (stage.get(stagingFile).blob.sha1.equals(
                    sha1OfFile(stagingFile))) {
                return;
            }
            // ...otherwise, at this point, the file has changed from the last staging,
            // so it needs to be re-staged, first remove the old StageObject:
            stage.remove(stagingFile);
            // ...but is identical to the version in _head.
            if (_head.content.containsKey(stagingFile)
                    && _head.content.get(stagingFile).sha1.equals(
                    sha1OfFile(stagingFile))) {
                return;
            }
        }

        // at this point, there will be a new staging for the file if there's any change to it
        // since the last commit.
        StageObject so = StageObject.of(stagingFile);
        if (so != null) {
            stage.put(stagingFile, so);
        }
    }

    private void recordLog() {
        // building log...
        StringBuilder sb = new StringBuilder();
        sb.append("===\n");
        sb.append("commit ").append(_head.sha1).append('\n');
        if (_head.parents.length > 1) {
            sb.append("Merge: ");
            for (int i = 0; i < _head.parents.length; i++) {
                sb.append(_head.parents[i].sha1, 0, 7);
                if (i != _head.parents.length - 1) {
                    sb.append(' ');
                } else {
                    sb.append('\n');
                }
            }
        }
        Date date = _head.timeStamp;
        SimpleDateFormat formatter =
                new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.ENGLISH);
        sb.append("Date: ").append(formatter.format(date)).append('\n');
        sb.append(_head.message).append("\n\n");
        String logStr = sb.toString();
        Utils.writeContents(Utils.join(LOG_DIR, _head.sha1),
                (_head.parents.length == 0
                        ? NULLSHA1 + "\n" : _head.parents[0].sha1 + "\n"), logStr);
        String oldHeadLog = Utils.readContentsAsString(HEAD_LOG_FILE);
        Utils.writeContents(HEAD_LOG_FILE, logStr, oldHeadLog);
        Utils.writeContents(GLOBAL_LOG_FILE, logStr, Utils.readContentsAsString(GLOBAL_LOG_FILE));
    }

    private static final String NULLSHA1 = "0000000000000000000000000000000000000000";

    public void commit(String msg) {
        commit(msg, new Commit[]{_head});
    }

    private void commit(String msg, Commit[] parents) {
        if (stage.isEmpty() && parents.length == 1 && parents[0] != null) {
            Main.exit("No changes added to the commit.");
        }
        if (msg.isEmpty()) {
            Main.exit("Please enter a commit message.");
        }
        _head = new Commit(msg, parents);
        branches.put(currentBranchName, _head);
        recordLog();
        commits.put(_head.sha1, _head);
    }

    public static void log() {
        String headId = Utils.readContentsAsString(HEAD_FILE);
        String logStr = Utils.readContentsAsString(HEAD_LOG_FILE);
        final String logHead = "===\ncommit ";
        String recordHeadId =
                logStr.substring(logHead.length(), logHead.length() + NULLSHA1.length());
        if (headId.equals(recordHeadId)) {
            System.out.print(logStr);
        } else {
            String newLog = rebuildLog(headId);
            Utils.writeContents(HEAD_LOG_FILE, newLog);
            System.out.println(newLog);
        }
    }

    private static String rebuildLog(String headId) {
        StringBuilder sb = new StringBuilder();
        String logStr = Utils.readContentsAsString(Utils.join(LOG_DIR, headId));
        String recordHeadId = logStr.substring(0, NULLSHA1.length());
        sb.append(logStr.substring(NULLSHA1.length() + 1));
        if (!recordHeadId.equals(NULLSHA1)) {
            sb.append(rebuildLog(recordHeadId));
        }
        return sb.toString();
    }

    public static void globalLog() {
        System.out.println(Utils.readContentsAsString(GLOBAL_LOG_FILE));
    }

    private void rm(File tobeRm) {
        if (!stage.containsKey(tobeRm) && !_head.content.containsKey(tobeRm)) {
            Main.exit("No reason to remove the file.");
        }
        // Unstage the file if it is currently staged for addition.
        stage.remove(tobeRm);
        // If the file is tracked in the current commit
        if (_head.content.containsKey(tobeRm)) {
            // stage it for removal,
            stage.put(tobeRm, null);
            // and remove the file from the working directory if the user has not already done so.
            if (tobeRm.exists()) {
                Utils.restrictedDelete(tobeRm);
            }
        }
    }

    public void rm(String fileName) {
        File tobeRm = Utils.join(CWD, fileName);
        rm(tobeRm);
    }

    public void save() {
        Utils.writeContents(HEAD_FILE, _head.sha1);
        Utils.writeObject(CORE_FILE, this);
    }

    public static List<String> find(String keyword) {
        List<String> ids = new ArrayList<>();
        for (Commit cmt : me.commits.values()) {
            if (cmt.message.contains(keyword)) {
                ids.add(cmt.sha1);
            }
        }
        return ids;
    }

    private ArrayList<File> allFilesInCWD() {
        List<String> allFileNames = Utils.plainFilenamesIn(CWD);
        if (allFileNames == null) {
            return null;
        }
        ArrayList<File> allFiles = new ArrayList<>();
        for (String fileName : allFileNames) {
            File file = Utils.join(CWD, fileName);
            allFiles.add(file);
        }
        return allFiles;
    }

    public String status() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Branches ===\n");
        for (String branchName : branches.keySet()) {
            sb.append(branchName.equals(currentBranchName)
                    ? ("*" + branchName) : branchName).append('\n');
        }
        // Working with Staged Files and Removed Files:
        TreeSet<String> stagedFiles = new TreeSet<>();
        TreeSet<String> removedFiles = new TreeSet<>();
        // Mapping file name of "modified but not staged" files to booleans,
        // true when modified, false when deleted.
        TreeMap<String, Boolean> modNotStaged = new TreeMap<>();
        for (Map.Entry<File, StageObject> entry : stage.entrySet()) {
            String fileName = entry.getKey().getName();
            if (entry.getValue() != null) { // file is staged for addition
                if (!entry.getKey().exists()) { // file has been deleted in workspace
                    modNotStaged.put(fileName, false);
                } else if (sha1OfFile(entry.getKey()).equals(
                        entry.getValue().blob.sha1)) {
                    // file exists but changed
                    stagedFiles.add(fileName);
                } else { // file exists and file changed
                    modNotStaged.put(fileName, true);
                }
            } else { // file is staged for removal
                removedFiles.add(fileName);
            }
        }
        sb.append("\n=== Staged Files ===\n");
        for (String f : stagedFiles) {
            sb.append(f).append('\n');
        }
        sb.append("\n=== Removed Files ===\n");
        for (String f : removedFiles) {
            sb.append(f).append('\n');
        }

        sb.append("\n=== Modifications Not Staged For Commit ===\n");
        ArrayList<File> allFiles = allFilesInCWD();
        if (allFiles != null) {
            for (File file : allFiles) {
                String sha1 = sha1OfFile(file);
                if ((/* Tracked in the current commit */ _head.content.containsKey(file)
                        /* changed in the working directory*/
                        && !sha1.equals(_head.content.get(file).sha1)
                        /* but not staged. */ && !stage.containsKey(file))) {
                    modNotStaged.put(file.getName(), true);
                }
            }
        }
        for (File f : _head.content.keySet()) { // or, tracked in the current commit,
            //  deleted from the working directory and not staged for removal
            if (!f.exists() && !stage.containsKey(f)) {
                modNotStaged.put(f.getName(), false);
            }
        }
        for (Map.Entry<String, Boolean> entry : modNotStaged.entrySet()) {
            if (entry.getValue()) {
                sb.append(entry.getKey()).append(" (modified)\n");
            } else {
                sb.append(entry.getKey()).append(" (deleted)\n");
            }
        }
        // "Untracked Files" is for files present in the working directory
        // but neither staged for addition nor tracked.
        // This includes files that have been staged for removal,
        // but then re-created without Gitlet’s knowledge.
        sb.append("\n=== Untracked Files ===\n");
        if (allFiles != null) {
            for (File f : allFiles) {
                if (!_head.content.containsKey(f) && stage.get(f) == null) {
                    sb.append(f.getName()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private List<File> untrackedFiles(List<File> allFiles) {
        ArrayList<File> list = new ArrayList<>();
        if (allFiles != null) {
            for (File f : allFiles) {
                if (!_head.content.containsKey(f) && stage.get(f) == null) {
                    list.add(f);
                }
            }
        }
        return list;
    }

    public static Repository load() {
        me = Utils.readObject(CORE_FILE, Repository.class);
        return me;
    }

    /**
     * Make a new branch.
     *
     * @param newBranchName new branch name
     */
    public void makeBranch(String newBranchName) {
        if (branches.containsKey(newBranchName)) {
            Main.exit("A branch with that name already exists.");
        }
        branches.put(newBranchName, _head);
        Repository.copyFile(HEAD_LOG_FILE, Utils.join(BRANCH_LOG_DIR, newBranchName));
    }

    /**
     * Fetches a commit by preceding characters in its id.
     *
     * @param shortId preceding characters in the commit id.
     * @return the fetched commit, null if no commit with that shortId found.
     */
    private Commit getCommitByShortId(String shortId) {
        for (Map.Entry<String, Commit> entry : commits.entrySet()) {
            if (entry.getKey().startsWith(shortId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void checkoutBranch(String branchName) {
        if (branchName.equals(currentBranchName)) {
            Main.exit("No need to checkout the current branch.");
        }
        Commit branchHead = branches.get(branchName);
        if (branchHead == null) {
            Main.exit("No such branch exists.");
            return;
        }
        Repository.copyFile(HEAD_LOG_FILE, Utils.join(BRANCH_LOG_DIR, currentBranchName));
        currentBranchName = branchName;
        Repository.copyFile(Utils.join(BRANCH_LOG_DIR, currentBranchName), HEAD_LOG_FILE);
        reset(branchHead);
    }

    public void checkout(String[] args) {
        Commit commit = null;
        String fileName = null;
        if (args.length > 2) {
            if (args.length == 3) {
                // checkout -- [file name]
                if (!args[1].equals("--")) {
                    Main.exit("Incorrect operands.");
                }
                commit = branches.get(currentBranchName);
                fileName = args[2];
            } else if (args.length == 4) {
                // checkout [commit id] -- [file name]
                if (!args[2].equals("--")) {
                    Main.exit("Incorrect operands.");
                }
                commit = getCommitByShortId(args[1]);
                fileName = args[3];
            }
            if (commit == null) {
                Main.exit("No commit with that id exists.");
                return;
            }
            if (!commit.restore(fileName)) {
                Main.exit("File does not exist in that commit.");
            }
        } else {
            // checkout [branch name]
            checkoutBranch(args[1]);
        }
    }

    public void removeBranch(String branchName) {
        if (!branches.containsKey(branchName)) {
            Main.exit("A branch with that name does not exist.");
        }
        if (branchName.equals(currentBranchName)) {
            Main.exit("Cannot remove the current branch.");
        }
        branches.remove(branchName);
    }

    /**
     * Checks out all the files tracked by the given commit.
     * Removes tracked files that are not present in that commit.
     * Also moves the current branch’s head to that commit node.
     * The commitId may be abbreviated as for checkout.
     * The staging area is cleared.
     * The command is essentially checkout of an arbitrary commit that
     * also changes the current branch head.
     *
     * @param commitId commit id, may be abbreviated.
     */
    public void reset(String commitId) {
        Commit commit = getCommitByShortId(commitId);
        if (commit == null) {
            Main.exit("No commit with that id exists.");
        }
        reset(commit);
    }

    private void reset(Commit commit) {
        if (mightOverwriteUntracked(commit)) {
            Main.exit("There is an untracked file in the way; "
                    + "delete it, or add and commit it first.");
        }
        for (File f : _head.content.keySet()) {
            Utils.restrictedDelete(f);
        }
        commit.restore();
        stage.clear();
        _head = commit;
        branches.put(currentBranchName, _head);
    }

    private void raiseConflict(File f, Commit merged) {
        File currentVersion = null;
        File branchVersion = null;
        if (_head.content.containsKey(f)) {
            currentVersion = _head.content.get(f).underlying;
        }
        if (merged.content.containsKey(f)) {
            branchVersion = merged.content.get(f).underlying;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<<<<<<< HEAD\n");
        sb.append(currentVersion == null ? "" : Utils.readContentsAsString(currentVersion));
        sb.append("=======\n");
        sb.append(branchVersion == null ? "" : Utils.readContentsAsString(branchVersion));
        sb.append(">>>>>>>\n");
        Utils.writeContents(f, sb.toString());
        add(f.getName());
    }

    private boolean mightOverwriteUntracked(Commit branchHead) {
        List<File> allFiles = allFilesInCWD();
        List<File> untracked = untrackedFiles(allFiles);
        for (File file : untracked) {
            if (!_head.content.containsKey(file) && branchHead.content.containsKey(file)
                    && !sha1OfFile(file).equals(branchHead.content.get(file).sha1)) {
                return true;
            }
        }
        return false;
    }

    public static String sha1OfFile(File file) {
        return Utils.sha1(file.getPath(), Utils.readContents(file));
    }

    public void merge(String branchName) {
        if (branchName.equals(currentBranchName)) {
            Main.exit("Cannot merge a branch with itself.");
        }
        if (!stage.isEmpty()) {
            Main.exit("You have uncommitted changes.");
        }
        Commit branchHead = branches.get(branchName);
        if (branchHead == null) {
            Main.exit("A branch with that name does not exist.");
            return;
        }
        Commit splitPoint = _head.findLatestCommonAncestor(branchHead);
        if (splitPoint.equals(branchHead)) {
            Main.exit("Given branch is an ancestor of the current branch.");
        }
        if (mightOverwriteUntracked(branchHead)) {
            Main.exit("There is an untracked file in the way; delete it, "
                    + "or add and commit it first.");
        }
        if (splitPoint.equals(_head)) {
            reset(branchHead);
            System.out.println("Current branch fast-forwarded.");
            return;
        }
        boolean noConflict = true;
        for (Map.Entry<File, Blob> entry : branchHead.content.entrySet()) {
            File f = entry.getKey();
            Blob b = entry.getValue();
            File branchVersion = b.underlying;
            if (!splitPoint.tracks(f)) {
                if (!_head.tracks(f)) {
                    b.checkout();
                    add(f.getName());
                } else if (!_head.hasBlob(b)) {
                    raiseConflict(f, branchHead);
                    noConflict = false;
                }
            } else {
                // the version in the given branch is modified
                if (!splitPoint.hasBlob(b)) {
                    // current branch does not track the file
                    if (!_head.tracks(f)
                            // or the file is tracked and modified in current branch
                            // but different from the given one.
                            || (!splitPoint.hasIdenticalFile(f) && !_head.hasBlob(b))) {
                        raiseConflict(f, branchHead);
                        noConflict = false;
                    } else {
                        b.checkout();
                        add(f.getName());
                    }
                }
            }
        }
        for (Map.Entry<File, Blob> entry : _head.content.entrySet()) {
            File f = entry.getKey();
            Blob b = entry.getValue();
            if (splitPoint.tracks(f) && !branchHead.tracks(f)) {
                if (!splitPoint.hasIdenticalFile(f)) {
                    raiseConflict(f, branchHead);
                    noConflict = false;
                } else {
                    rm(f);
                }
            }
        }
        commit("Merged " + branchName + " into " + currentBranchName + ".",
                new Commit[]{_head, branchHead});
        if (!noConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }
}
