package videoproxy;

import java.util.Vector;

public interface HLSLocalStreamProxyInterface {

	/**
	 * Tell the proxy which top-playlist-url to use. This method will block until everything has beed parsed 
	 * @param topPlaylistUrl
	 */
	void setUrl(String topPlaylistUrl) throws Exception;
	
	/**
	 * Get the current HLS playlist url
	 * @return
	 */
	String getUrl();
	
	/**
	 * Returns true if and only if the url is a correct HLS playlist
	 * @param topPlaylistUrl
	 * @return
	 */
	boolean validateUrl(String topPlaylistUrl);
	
	/**
	 * Get the avaliable qualitys in bps
	 * @return
	 */
	Vector<Float> getAvailableQualities();
	
	/**
	 * Choose which quality to continue buffering with.
	 * @param qualityIndex Index as seen in getAvailableQualitys
	 */
	void setQuality(int qualityIndex);
	
	/**
	 * True if there is another video file to play
	 * @return
	 */
	boolean hasNextLocalVideoUrl();

	/**
	 * Returns the next video url
	 * @return
	 */
	String getNextLocalVideoUrl();
}
