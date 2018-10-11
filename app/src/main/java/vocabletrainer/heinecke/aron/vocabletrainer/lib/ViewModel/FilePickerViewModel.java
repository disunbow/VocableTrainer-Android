package vocabletrainer.heinecke.aron.vocabletrainer.lib.ViewModel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.ViewModel;
import android.content.Context;
import android.os.FileObserver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import vocabletrainer.heinecke.aron.vocabletrainer.R;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Formatter;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.BasicFileEntry;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.FileEntry;

import static vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.BasicFileEntry.TYPE_INTERNAL;
import static vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.BasicFileEntry.TYPE_SD_CARD;
import static vocabletrainer.heinecke.aron.vocabletrainer.lib.StorageUtils.SD_CARD;
import static vocabletrainer.heinecke.aron.vocabletrainer.lib.StorageUtils.getAllStorageLocations;

/**
 * ViewModel for file picker
 */
public class FilePickerViewModel extends ViewModel {
    private static final String TAG = "FilePickerViewModel";
    private final Object lock;
    private File currentMedia;
    private File currentFolder;
    private MutableLiveData<String> pathString;
    private MutableLiveData<ArrayList<BasicFileEntry>> viewList;
    private MutableLiveData<String> error;
    private boolean writeMode;
    private Formatter formatter;
    private final BasicFileEntry ENTRY_UP;
    private FileObserver fileObserver;
    private MutableLiveData<File> pickedFile;
    private boolean fileObserverInitialTrigger;
    private FileEntry preselectedElement;
    private AtomicBoolean dirChange;
    private boolean actionUpAllowed;

    public FilePickerViewModel() {
        lock = new Object();
        dirChange = new AtomicBoolean(false);
        formatter = new Formatter();
        currentFolder = null;
        currentMedia = null;
        actionUpAllowed = false;
        ENTRY_UP = new BasicFileEntry("..", "", 0, BasicFileEntry.TYPE_UP, true);
        pathString = new MutableLiveData<>();
        viewList = new MutableLiveData<>();
        error = new MutableLiveData<>();
        pickedFile = new MutableLiveData<>();
    }

    /**
     * Whether up-action is allowed currently
     * @return
     */
    public boolean isActionUpAllowed() {
        return actionUpAllowed;
    }

    /**
     * Position of preselected element
     * @return
     */
    public FileEntry getPreselectedElement() {
        return preselectedElement;
    }

    @Override
    protected void onCleared() {
        if(fileObserver != null)
            fileObserver.stopWatching();
        super.onCleared();
    }

    /**
     * Set view with pre-selected file
     * @param file
     * @return success
     */
    public boolean setPickedFile(File file, Context context){
        if(file.isFile() && file.canRead()){
            if(writeMode && !file.canWrite()){
                return false;
            }
            File folder = file.getParentFile();
            return setViewFile(folder,file, context);
        }
        return false;
    }

    public LiveData<File> getPickedFileHandle() {
        return pickedFile;
    }

    public LiveData<String> getPathStringHandle() {
        return pathString;
    }

    public LiveData<ArrayList<BasicFileEntry>> getViewListHandle() {
        return viewList;
    }

    public LiveData<String> getErrorHandle() {
        return error;
    }

    /**
     * Reset error to null
     */
    public void resetError(){
        error.setValue(null);
    }

    public void setWriteMode(boolean writeMode) {
        this.writeMode = writeMode;
    }

    private boolean isMediaRoot(File path, Context context){
        return getAllStorageLocations(context).containsValue(path);
    }

    /**
     * Returns media root of path
     * @param path
     * @return Entry of Name,File for Media
     */
    @Nullable
    private Map.Entry<String,File> getPathMedia(File path, Context context){
        for(Map.Entry<String,File> media : getAllStorageLocations(context).entrySet()){
            if(path.getAbsolutePath().startsWith(media.getValue().getAbsolutePath()))
                return media;
        }
        return null;
    }

    /**
     * Go to parent directory
     */
    public void goUp(Context context){
        dirChange.set(true);
        if(getAllStorageLocations(context).containsValue(currentFolder)){
            showMediaSelection(context);
        } else {
            if (!setViewFile(currentFolder.getParentFile(), context)) {
                showMediaSelection(context);
            }
        }
        dirChange.set(false);
    }

    public boolean setViewFile(File file, Context context){
        return setViewFile(file,null, context);
    }

    /**
     * Set view to location of specified folder
     * @param file
     * @param pickedFile File to pick
     * @return success, false on failure (permissions.. )
     */
    public boolean setViewFile(File file, @Nullable File pickedFile, @NonNull Context context){
        synchronized (lock) {
            Log.d(TAG, "new File: " + file.getAbsolutePath());
            if (file.exists() && file.isDirectory() && file.canRead()) {
                if (fileObserver != null) {
                    fileObserver.stopWatching();
                    fileObserver = null;
                }
                if (writeMode && !file.canWrite()) {
                    error.postValue(context.getString(R.string.File_Error_NoWrite_Perm)+ file.getAbsolutePath());
                    return false;
                }
                Map.Entry<String, File> media = getPathMedia(file, context);
                if (media == null) {
                    error.postValue(context.getString(R.string.File_Error_No_Media_For_File)+ file.getAbsolutePath());
                    return false;
                }
                ArrayList<BasicFileEntry> entryList = new ArrayList<>();
                entryList.add(ENTRY_UP);
                File[] files = file.listFiles();
                if (files == null) {
                    error.postValue(context.getString(R.string.File_Error_Nullpointer));
                    return false;
                }
                for (File entry : files) {
                    FileEntry fileEntry = new FileEntry(entry, formatter);
                    if (pickedFile != null && fileEntry.getFile().getAbsolutePath().equals(pickedFile.getAbsolutePath())) {
                        fileEntry.setSelected(true);
                        preselectedElement = fileEntry;
                    }
                    entryList.add(fileEntry);
                }
                currentFolder = file;
                pathString.postValue(media.getKey() + file.getAbsolutePath().replace(media.getValue().getAbsolutePath(), ""));
                viewList.postValue(entryList);
                fileObserver = new FileObserver(file.getAbsolutePath()) {
                    @Override
                    public void onEvent(int event, @Nullable String path) {
                        if (fileObserverInitialTrigger) {
                            fileObserverInitialTrigger = false;
                            return;
                        }
                        if(dirChange.get()){ // prevent trigger during view change
                            return;
                        }
                        Log.d(TAG, "file observer triggered "+path);
                        setViewFile(file, context); // re-trigger, refreshing view
                    }
                };
                actionUpAllowed = true;
                fileObserverInitialTrigger = true;
                fileObserver.startWatching();
                return true;
            }
            return false;
        }
    }

    /**
     * Returns the current folder<br>
     *     Null if in media selection view
     * @return
     */
    @Nullable
    public File getCurrentFolder() {
        return currentFolder;
    }

    /**
     * Display media selection screen
     */
    public void showMediaSelection(Context context){
        synchronized (lock) {
            if (fileObserver != null) {
                fileObserver.stopWatching();
                fileObserver = null;
            }
            Map<String, File> storages = getAllStorageLocations(context);
            ArrayList<BasicFileEntry> entriesList = new ArrayList<>(storages.size());
            for (Map.Entry<String, File> entry : storages.entrySet()) {
                // SD_CARD is here for internal storage, differentiating between SD_CARD and EXTERN_SD_CARD..
                entriesList.add(new FileEntry(entry.getValue(), entry.getKey().equals(SD_CARD) ? TYPE_INTERNAL : TYPE_SD_CARD, entry.getKey()));
            }
            currentFolder = null;
            actionUpAllowed = false;
            pathString.postValue("/");
            viewList.postValue(entriesList);
        }
    }

    /**
     * Resets preselected element to null
     */
    public void resetPreselectedElement() {
        preselectedElement = null;
    }
}
