import java.sql.Timestamp;
import java.util.logging.Logger;

public class CacheData {
    private static final Logger logger = Logger.getLogger(
            JHTTP.class.getCanonicalName());

    private byte [] _fileData;
    private Timestamp _timeStamp;

    public CacheData(byte [] fileData){
        this._timeStamp = new Timestamp(System.currentTimeMillis());
        logger.info("Timestamp is: " + this._timeStamp.toString());
        this._fileData = fileData;
    }

    public Timestamp gettimeStamp(){
        return this._timeStamp;
    }

    public byte[] getfileData(){
        return this._fileData;
    }

    public void settimeStamp(){
        this._timeStamp = new Timestamp(System.currentTimeMillis());
        logger.info("New timestamp is: " + this._timeStamp.toString());
    }

    public boolean isOlderTimeStamps(CacheData oldCache){
        return this._timeStamp.before(oldCache.gettimeStamp());
    }
}
