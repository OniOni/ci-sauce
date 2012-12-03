package com.saucelabs.ci;

import com.saucelabs.sauceconnect.SauceConnect;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.io.*;
import java.net.URISyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Ross Rowe
 */
public abstract class SauceLibraryManager {
    
    private static final Logger logger = Logger.getLogger(SauceLibraryManager.class);
    private static final String VERSION_CHECK_URL = "https://saucelabs.com/versions.json";
    private static final int BUFFER = 1024;
    private static final String SAUCE_CONNECT_KEY = "Sauce Connect 2";
    private static final String VERSION_KEY = "version";
    private static final String DOWNLOAD_URL_KEY = "download_url";

    /**
     * Performs a REST request to https://saucelabs.com/versions.json to retrieve the list of
     * sauce connect version information.  If the version information in the response is later
     * than the current version, then return true.
     *
     * @return boolean indicating whether an updated sauce connect jar is available
     * @throws java.io.IOException
     * @throws java.net.URISyntaxException
     */
    public boolean checkForLaterVersion() throws IOException, URISyntaxException {
        
        logger.info("Checking for updates to Sauce Connect");
        String response = getSauceAPIFactory().doREST(VERSION_CHECK_URL);
//        String response = IOUtils.toString(getClass().getResourceAsStream("/versions.json"));
        int version = extractVersionFromResponse(response);
        //compare version attribute against SauceConnect.RELEASE()
        return version > (Integer) SauceConnect.RELEASE();
    }

    /**
     * Performs a REST request to https://saucelabs.com/versions.json to retrieve the download url
     * to be used to retrieve the latest version of Sauce Connect, then updates the Bamboo Sauce plugin jar
     * to include this jar.
     *
     * @throws java.io.IOException
     * @throws java.net.URISyntaxException
     */
    public void triggerReload() throws IOException, URISyntaxException {
        logger.info("Updating Sauce Connect");
        String response = getSauceAPIFactory().doREST(VERSION_CHECK_URL);
//        String response = IOUtils.toString(getClass().getResourceAsStream("/versions.json"));
        File jarFile = retrieveNewVersion(response);
        updatePluginJar(jarFile);
    }

    public SauceFactory getSauceAPIFactory() {
        return new SauceFactory();
    }

    public abstract void updatePluginJar(File jarFile) throws IOException, URISyntaxException;

    public int extractVersionFromResponse(String response) {
        JSONObject versionObject = (JSONObject) JSONValue.parse(response);
        JSONObject sauceConnect2 = (JSONObject) versionObject.get(SAUCE_CONNECT_KEY);
        String versionText = (String) sauceConnect2.get(VERSION_KEY);
        //extract the last digits after the -
        String versionNumber = StringUtils.substringAfter(versionText, "-r");
        if (StringUtils.isBlank(versionNumber)) {
            //TODO throw an error
            return 0;
        } else {
            return Integer.parseInt(versionNumber);
        }
    }

    /**
     * Performs a HTTP GET to retrieve the contents of the download url (assumed to be a zip
     * file), then unzips the zip file to the file system.
     *
     * @param response
     * @return
     * @throws java.io.IOException
     */
    public File retrieveNewVersion(String response) throws IOException {
        //perform HTTP get for download_url
        String downloadUrl = extractDownloadUrlFromResponse(response);
        byte[] bytes = getSauceAPIFactory().doHTTPGet(downloadUrl);
//        byte[] bytes = FileUtils.readFileToByteArray(new File("C:/Sauce-Connect.zip"));
        //unzip contents to temp directory
        return unzipByteArray(bytes);
    }
    
    

    /**
     * Extracts the contents of the byte array to the temp drive.
     *
     * @param byteArray
     * @return a {@link java.io.File} instance pointing to the Sauce Connect jar file
     */
    private File unzipByteArray(byte[] byteArray) {
        File destPath = new File(System.getProperty("java.io.tmpdir"));

        File jarFile = null;
        try {
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(byteArray));
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                int count;
                byte data[] = new byte[BUFFER];
                // write the files to the disk

                File destFile = new File(destPath, entry.getName());
                if (!destFile.getParentFile().exists()) {
                    boolean result = destFile.getParentFile().mkdirs();
                    if (!result) {
                        logger.error("Unable to create directories, attempting to continue");
                    }
                }

                FileOutputStream fos = new FileOutputStream(destFile);
                BufferedOutputStream dest = new BufferedOutputStream(fos, BUFFER);
                while ((count = zis.read(data, 0, BUFFER)) != -1) {
                    dest.write(data, 0, count);
                }
                dest.flush();
                dest.close();
                if (entry.getName().endsWith("jar")) {
                    jarFile = destFile;
                }
            }
            zis.close();
        }
        catch (IOException e) {
            logger.error("Error unzipping contents", e);
        }
        return jarFile;
    }

    /**
     * Retrieves the download url from the JSON response
     *
     * @param response
     * @return String representing the URL to use to download the sauce connect jar
     */
    private String extractDownloadUrlFromResponse(String response) {
        JSONObject versionObject = (JSONObject) JSONValue.parse(response);
        JSONObject sauceConnect2 = (JSONObject) versionObject.get(SAUCE_CONNECT_KEY);
        return (String) sauceConnect2.get(DOWNLOAD_URL_KEY);
    }




}
