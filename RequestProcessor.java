import sun.misc.Cache;

import java.io.*;

import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class RequestProcessor implements Runnable {
    //our we page is so small that we dont want to make the cache too big in comparison
    private static final int CACHE_SIZE = 2;
    private static final Logger logger = Logger.getLogger(
            JHTTP.class.getCanonicalName());

    private File rootDirectory;
    private String indexFileName = "index.html";
    private ConcurrentHashMap<String, CacheData> cache;
    private Socket connection;

    public RequestProcessor(File rootDirectory, String indexFileName, ConcurrentHashMap cache, Socket connection) {
        if (rootDirectory.isFile()) {
            throw new IllegalArgumentException(
                    "rootDirectory must be a directory, not a file");
        }
        try {
            rootDirectory = rootDirectory.getCanonicalFile();
        } catch (IOException ex) {
        }
        this.rootDirectory = rootDirectory;

        if (indexFileName != null) this.indexFileName = indexFileName;
        this.connection = connection;

        this.cache = cache;
    }

    @Override
    public void run() {
        // for security checks
        String root = rootDirectory.getPath();
        try {
            OutputStream raw = new BufferedOutputStream(
                    connection.getOutputStream()
            );
            Writer out = new OutputStreamWriter(raw);
            Reader in = new InputStreamReader(
                    new BufferedInputStream(
                            connection.getInputStream()
                    ), "US-ASCII"
            );
            StringBuilder requestLine = new StringBuilder();
            while (true) {
                int c = in.read();
                if (c == '\r' || c == '\n') break;
                requestLine.append((char) c);
            }

            String get = requestLine.toString();

            logger.info(connection.getRemoteSocketAddress() + " " + get);

            String[] tokens = get.split("\\s+");
            String method = tokens[0];
            String version = "";
            byte[] body = null;
            String cacheFileKey = null;

            if (method.equals("GET")) {
                String fileName = tokens[1];
                if (fileName.endsWith("/")) fileName += indexFileName;
                String contentType =
                        URLConnection.getFileNameMap().getContentTypeFor(fileName);
                if (tokens.length > 2) {
                    version = tokens[2];
                }

                //check cache first
                if (cache.containsKey(fileName)){
                    sendCache(raw, out, version, contentType, fileName, true);
                }
                else { //file not found in cache must retrieve
                    File theFile = new File(rootDirectory,
                            fileName.substring(1, fileName.length()));

                    if (theFile.canRead()
                            // Don't let clients outside the document root
                            && theFile.getCanonicalPath().startsWith(root)) {
                        body = Files.readAllBytes(theFile.toPath());
                        if (version.startsWith("HTTP/")) { // send a MIME header
                            sendHeader(out, "HTTP/1.0 200 OK", contentType, body.length);
                        }

                        // send the file; it may be an image or other binary data
                        // so use the underlying output stream
                        // instead of the writer
                        raw.write(body);
                        raw.flush();
                        cacheFileKey = fileName;
                    } else { // can't find the file
                        String string_body = new StringBuilder("<HTML>\r\n")
                                .append("<HEAD><TITLE>File Not Found</TITLE>\r\n")
                                .append("</HEAD>\r\n")
                                .append("<BODY>")
                                .append("<H1>HTTP Error 404: File Not Found</H1>\r\n")
                                .append("</BODY></HTML>\r\n").toString();
                        if (version.startsWith("HTTP/")) { // send a MIME header
                            sendHeader(out, "HTTP/1.0 404 File Not Found",
                                    "text/html; charset=utf-8", string_body.length());
                        }
                        logger.info("Can't find the file");

                        out.write(string_body);
                        out.flush();
                    }
                }
            } else if (method.equals("HEAD")) {

                String fileName = tokens[1];
                if (fileName.endsWith("/")) fileName += indexFileName;
                String contentType =
                        URLConnection.getFileNameMap().getContentTypeFor(fileName);
                if (tokens.length > 2) {
                    version = tokens[2];
                }

                //check cache
                if (cache.containsKey(fileName)){
                    sendCache(raw, out, version, contentType, fileName, false);
                }
                else { //can't find in cache
                    File theFile = new File(rootDirectory,
                            fileName.substring(1, fileName.length()));

                    if (theFile.canRead()
                            // Don't let clients outside the document root
                            && theFile.getCanonicalPath().startsWith(root)) {
                        body = Files.readAllBytes(theFile.toPath());
                        if (version.startsWith("HTTP/")) { // send a MIME header
                            sendHeader(out, "HTTP/1.0 200 OK", contentType, body.length);
                        }
                        cacheFileKey = fileName;
                    } else { // can't find the file
                        String string_body = new StringBuilder("<HTML>\r\n")
                                .append("<HEAD><TITLE>File Not Found</TITLE>\r\n")
                                .append("</HEAD>\r\n")
                                .append("<BODY>")
                                .append("<H1>HTTP Error 404: File Not Found</H1>\r\n")
                                .append("</BODY></HTML>\r\n").toString();
                        if (version.startsWith("HTTP/")) { // send a MIME header
                            sendHeader(out, "HTTP/1.0 404 File Not Found",
                                    "text/html; charset=utf-8", string_body.length());
                        }
                        logger.warning("Can't find the file");
                    }
                }
            }//end of HEAD
            else if (method.equals("POST")) {

                //this block is used to get the data before the body of the request
                String request = "";
                int count = 0;
                while (true) {
                    int c = in.read();
                    request += (char) c;
                    if (c == '\r' || c == '\n')
                        count++;
                    else
                        count = 0;
                    if (count == 4)
                        break;
                }
                //System.out.println(request);
                //get the content length from the request
                Scanner scan = new Scanner(request);
                scan.useDelimiter("Content-Length: ");
                scan.next();
                String second = scan.next();
                scan = new Scanner(second).useDelimiter("\r\n");
                int contentLength = scan.nextInt();
                scan.close();
                //get the data (query String html) that the user submitted
                String postInformation = "";
                for (int i = 0; i < contentLength; i++) {
                    postInformation += (char) in.read();
                }
                //store the username and password into strings
                String[] splitAnd = postInformation.split("&");
                String username = splitAnd[0].split("=")[1];
                String password = splitAnd[1].split("=")[1];

                //dealing with authentication
                File input = new File("C:/Users/arian_000/IdeaProjects/WebArch/src/authenticationList.txt");
                Scanner auth = new Scanner(input);
                ArrayList<String> listOfAllowed = new ArrayList<String>();
                while (auth.hasNext()) {
                    listOfAllowed.add(auth.nextLine());
                }
                //check to see if the user submitted data is in the file which has allowed users
                boolean authStatus = false;
                for (int i = 0; i < listOfAllowed.size(); i++) {
                    if (listOfAllowed.get(i).equalsIgnoreCase(username)) {
                        if (listOfAllowed.get(i + 1).equalsIgnoreCase(password)) {
                            authStatus = true;
                            break;
                        }
                    }
                }
                auth.close();

                //dealing with authorization
                Scanner authorization = new Scanner(new File("C:/Users/arian_000/IdeaProjects/WebArch/src/authorizationList.txt"));
                ArrayList<String> authorizationList = new ArrayList<String>();

                while (authorization.hasNext()) {
                    authorizationList.add(authorization.nextLine());
                }

                boolean authorizationStatus = false;
                for (int i = 0; i < authorizationList.size(); i++) {
                    if (authorizationList.get(i).equalsIgnoreCase(username)) {
                        if (authorizationList.get(i + 1).equalsIgnoreCase(password)) {
                            authorizationStatus = true;
                            break;
                        }
                    }
                }

                authorization.close();

                //displaying information on web page based on authentication / authorization status
                if (authStatus) {
                    if (authorizationStatus) {
                        //maximum access
                        String fileName = "C:/Users/arian_000/IdeaProjects/WebArch/src/authorizedHTML.txt";
                        byte[] fileData = Files.readAllBytes(new File(fileName).toPath());
                        if (version.startsWith("HTTP/")) { // send a MIME header
                            sendHeader(out, "HTTP/1.0 200 Good",
                                    "text/html; charset=utf-8", fileData.length);
                        }
                        raw.write(fileData);
                        raw.flush();

                    } else {
                        //passed but regular user
                        String fileName = "C:/Users/arian_000/IdeaProjects/WebArch/src/notAuthorizedHTML.txt";
                        byte[] fileData = Files.readAllBytes(new File(fileName).toPath());
                        if (version.startsWith("HTTP/")) { // send a MIME header
                            sendHeader(out, "HTTP/1.0 200 Good",
                                    "text/html; charset=utf-8", fileData.length);
                        }
                        raw.write(fileData);
                        raw.flush();
                    }
                } else { //invalid login information
                    String string_body = new StringBuilder("<HTML>\r\n")
                            .append("<HEAD><TITLE> Hello! </TITLE>\r\n")
                            .append("</HEAD>\r\n")
                            .append("<BODY>")
                            .append("<H1>You have invalid credentials </H1>\r\n")
                            .append("</BODY></HTML>\r\n").toString();

                    if (version.startsWith("HTTP/")) { // send a MIME header
                        sendHeader(out, "HTTP/1.0 200 Good",
                                "text/html; charset=utf-8", string_body.length());
                    }
                    logger.warning("This person has invalid credentials");

                    out.write(string_body);
                    out.flush();
                }
            }//end post
            else { // method does not equal to what we implemented , GET, HEAD, POST
                String string_body = new StringBuilder("<HTML>\r\n")
                        .append("<HEAD><TITLE>Not Implemented</TITLE>\r\n")
                        .append("</HEAD>\r\n")
                        .append("<BODY>")
                        .append("<H1>HTTP Error 501: Not Implemented</H1>\r\n")
                        .append("</BODY></HTML>\r\n").toString();
                if (version.startsWith("HTTP/")) { // send a MIME header
                    sendHeader(out, "HTTP/1.0 501 Not Implemented",
                            "text/html; charset=utf-8", string_body.length());
                }
                logger.info("Incorrect method implemented");
                out.write(string_body);
                out.flush();
            }

            //save data to cache
            if (body != null) {
                CacheData cacheEntry = new CacheData(body);
                //our we page is so small that we dont want to make the cache too big in comparison
                logger.info("Caching " + cacheFileKey);
                if (cache.size() < CACHE_SIZE) {
                    cache.put(cacheFileKey, cacheEntry);
                } else {
                    String oldestCacheFileKey = null;
                    for (Enumeration<String> cacheTable = cache.keys(); cacheTable.hasMoreElements(); ) {
                        String entryKey = cacheTable.nextElement();
                        if (oldestCacheFileKey == null) oldestCacheFileKey = entryKey;
                        else {
                            CacheData entryValue = cache.get(entryKey);
                            if (entryValue.isOlderTimeStamps(cache.get(oldestCacheFileKey)) == true) {
                                //this means that the next entry was older than the previous
                                //update
                                oldestCacheFileKey = entryKey;
                            }
                        }
                    }
                    //once we have found the oldest cache entry,
                    //replace it with the new cache item
                    cache.remove(oldestCacheFileKey);
                    cache.put(cacheFileKey, cacheEntry);
                }
            }//end cache
        } catch (IOException ex) {
            logger.log(Level.WARNING,
                    "Error talking to " + connection.getRemoteSocketAddress(), ex);
        } finally {
            try {
                connection.close();
            } catch (IOException ex) {
            }
        }
    }

    private void sendHeader(Writer out, String responseCode,
                            String contentType, int length)
            throws IOException {
        out.write(responseCode + "\r\n");
        Date now = new Date();
        out.write("Date: " + now + "\r\n");
        out.write("Server: JHTTP 2.0\r\n");
        out.write("Content-length: " + length + "\r\n");
        out.write("Content-type: " + contentType + "\r\n\r\n");
        out.flush();
    }

    private void sendCache(OutputStream raw, Writer out, String version,
                           String contentType, String file, boolean GET)
            throws IOException {
            //access data from cache
            logger.info("Loading " + file + " from cache");
            byte[] body = cache.get(file).getfileData();
            //send header information
            if (version.startsWith("HTTP/")) { // send a MIME header
                sendHeader(out, "HTTP/1.0 200 OK", contentType, body.length);
            }
            if (GET == true) {
                raw.write(body);
                raw.flush();
            }
            //update timestamp
            cache.get(file).settimeStamp();

    }
}
