package org.microprofileext.config.source.base.file;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.microprofileext.config.event.ChangeEventNotifier;
import org.microprofileext.config.source.base.EnabledConfigSource;


/**
 * URL Based property files
 * 
 * Load some file from a file and convert to properties.
 * @author <a href="mailto:phillip.kruger@phillip-kruger.com">Phillip Kruger</a>
 */
@Log
public abstract class AbstractUrlBasedSource extends EnabledConfigSource implements Reloadable {
    
    private final Map<String, String> properties = new HashMap<>();
    private String urlInputString;
    private String keySeparator;
    private boolean pollForChanges;
    private int pollInterval;
    private boolean notifyOnChanges;
    
    private FileWatcher fileWatcher = null;
    
    public AbstractUrlBasedSource(){
        init();
    }
    
    private void init(){
        String ext = getFileExtension();
        log.log(Level.INFO, "Loading [{0}] MicroProfile ConfigSource", ext);
        this.keySeparator = loadPropertyKeySeparator();
        this.notifyOnChanges = loadNotifyOnChanges();
        this.pollForChanges = loadPollForChanges();
        this.pollInterval = loadPollInterval();
        
        if(this.pollForChanges && this.pollInterval>0){
            log.log(Level.INFO, "  Polling for changes in {0} every {1} seconds", new Object[]{ext, pollInterval});
            this.fileWatcher = new FileWatcher(this,pollInterval);
        }
        
        this.urlInputString = loadUrlPath();
        this.loadUrls(urlInputString);
        super.initOrdinal(500); 
    }
    
    
    @Override
    protected Map<String, String> getPropertiesIfEnabled() {
        return this.properties;
    }

    @Override
    public String getValue(String key) {
        // in case we are about to configure ourselves we simply ignore that key
        if(super.isEnabled() && !key.startsWith(getPrefix())){
            return this.properties.get(key);
        }
        return null;
    }

    @Override
    public String getName() {
        return getClassKeyPrefix() + UNDERSCORE + this.urlInputString;
    }
    
    @Override
    public void reload(URL url){
        Map<String, String> before = new HashMap<>(this.properties);
        this.properties.clear();
        initialLoad(url);
        Map<String, String> after = new HashMap<>(this.properties);
        
        if(notifyOnChanges)ChangeEventNotifier.getInstance().detectChangesAndFire(before, after,getName());
    }
    
    private void initialLoad(URL url){
        
        log.log(Level.INFO, "Using [{0}] as {1} URL", new Object[]{url.toString(), getFileExtension()});
        
        InputStream inputStream = null;
        
        try {
            inputStream = url.openStream();
            if (inputStream != null) {
                this.properties.putAll(toMap(inputStream));
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Unable to read URL [{0}] - {1}", new Object[]{url, e.getMessage()});
        } finally {
            try {
                if (inputStream != null)inputStream.close();
            // no worries, means that the file is already closed
            } catch (IOException e) {}
        }
    }
    
    protected String getKeySeparator(){
        return this.keySeparator;
    }
    
    private void loadUrls(String surl) {
        String urls[] = surl.split(COMMA);
        
        for(String u:urls){
            if(u!=null && !u.isEmpty()){
                loadUrl(u.trim());
            }
        }
    }
    
    private void loadUrl(String url) {
        try {
            URL u = new URL(url);
            initialLoad(u);
            // TODO: Add support for other protocols ? 
            if(this.fileWatcher!=null && u.getProtocol().equalsIgnoreCase(FILE))this.fileWatcher.startWatching(u);
        } catch (MalformedURLException ex) {
            log.log(Level.WARNING, "Can not load URL [" + url + "]", ex);
        }
    }
    
    private String getConfigKey(String subKey){
        return getPrefix() + subKey;
    }
    
    private String loadPropertyKeySeparator(){
        return getConfig().getOptionalValue(getConfigKey(KEY_SEPARATOR), String.class).orElse(DOT);
    }
    
    private boolean loadPollForChanges(){
        return getConfig().getOptionalValue(getConfigKey(POLL_FOR_CHANGES), Boolean.class).orElse(DEFAULT_POLL_FOR_CHANGES);
    }
    
    private boolean loadNotifyOnChanges(){
        return getConfig().getOptionalValue(getConfigKey(NOTIFY_ON_CHANGES), Boolean.class).orElse(DEFAULT_NOTIFY_ON_CHANGES);
    }
    
    private int loadPollInterval(){
        return getConfig().getOptionalValue(getConfigKey(POLL_INTERVAL), Integer.class).orElse(DEFAULT_POLL_INTERVAL);
    }
    
    private String loadUrlPath(){
        return getConfig().getOptionalValue(getConfigKey(URL), String.class).orElse(getDefaultUrl());
    }
    
    private String getPrefix(){
        return CONFIGSOURCE + DOT + getFileExtension() + DOT;
    }
    
    private String getDefaultUrl(){
        String path = APPLICATION + DOT + getFileExtension();
        try {
            URL u = Paths.get(path).toUri().toURL();
            return u.toString();
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private static final String COMMA = ",";
    private static final String UNDERSCORE = "_";
    private static final String DOT = ".";
    private static final String URL = "url";
    private static final String KEY_SEPARATOR = "keyseparator";
    
    private static final String POLL_FOR_CHANGES = "pollForChanges";
    private static final boolean DEFAULT_POLL_FOR_CHANGES = false;
    
    private static final String NOTIFY_ON_CHANGES = "notifyOnChanges";
    private static final boolean DEFAULT_NOTIFY_ON_CHANGES = true;
    
    private static final String POLL_INTERVAL = "pollInterval";
    private static final int DEFAULT_POLL_INTERVAL = 5; // 5 seconds
    
    private static final String CONFIGSOURCE = "configsource";
    private static final String APPLICATION = "application";
    private static final String FILE = "file";
    
    protected abstract String getFileExtension();
    protected abstract Map<String,String> toMap(final InputStream inputStream);
}