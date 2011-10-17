package se.miun.hls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import videoproxy.BufferedVideoFile;
import videoproxy.BufferedVideoFileEventListener;
import videoproxy.HLSLocalStreamProxyEventListener;
import videoproxy.HLSLocalStreamProxyInterface;
import android.net.Uri;
import android.util.Log;

public class HLSLocalStreamProxy implements HLSLocalStreamProxyInterface,
		BufferedVideoFileEventListener {

	private final static int CONF_BUFFER_SIZE = 3;

	private HLSLocalStreamProxyEventListener listener = null;
	private static final String FILETYPE_PLAYLIST = "m3u8";

	private String TAG = this.getClass().getSimpleName();

	private String baseUrl = "";
	private HashMap<Float, String> playlistQualityUrlMap = new HashMap<Float, String>();
	private Vector<String> videoFileNames = new Vector<String>();

	private int currentQuality = 0;
	private int currentCachedVideoFile = 0;
	private int currentPlayingVideoFile = 0;

	private int qualityChangeMillisecondOffset = 0;
	private int qualityChangeFetchMillisecondOffset = 0;

	private Vector<BufferedVideoFile> bufferedVideoParts = new Vector<BufferedVideoFile>();
	private String fullUrl;

	private int listenPort = 0;
	private ServerSocket srvSock;

	protected boolean runSocketLoop = true;

	// private ServerSocket listenSock

	public HLSLocalStreamProxy(HLSLocalStreamProxyEventListener listener,
			int listenPort) {
		this.listener = listener;
		this.listenPort = listenPort;
		try {
			this.srvSock = new ServerSocket(this.listenPort);
			listenSockAndStreamVideo();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void listenSockAndStreamVideo() {
		Executors.newSingleThreadExecutor().submit(new Runnable() {
			@Override
			public void run() {
				HLSLocalStreamProxy parent = HLSLocalStreamProxy.this;

				try {
					while (!srvSock.isClosed() && parent.runSocketLoop) {
						try {
							Socket sock = parent.srvSock.accept();
							OutputStream os = sock.getOutputStream();

							String headers = "HTTP/1.0 200 OK\nContent-Type: application/video; charset=UTF-8\nContent-Length: 11789782\nDate: Thu, 13 Oct 2011 00:34:08 GMT\n\n";
							os.write(headers.getBytes());
							os.flush();

							for (int i = 0; i < 180; i++) {

								BufferedVideoFile buff = parent.bufferedVideoParts
										.get(i);

								if (!buff.isReady) {
									for (int sleeper = 0; sleeper < 2000; sleeper++) {
										Thread.sleep(10);
										if (buff.isReady)
											break;
									}
								}

								buff.streamTo(os);

								startCacheingVideo(
										++parent.currentCachedVideoFile, false);
							}
							sock.close();

						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * If the resource is a m3u8 playlist it will be fetched and parsed, if it
	 * is a videofile it will be added in the list. In effect this fetches all
	 * the leaf nodes (actual media http-links) in the playlist-tree.
	 * 
	 * @param resourceUri
	 * @throws Exception
	 */
	public void parseAndAddToList(Uri resourceUri, boolean root)
			throws Exception {

		String filename = resourceUri.getLastPathSegment().toString();
		String extention = "";
		String split[] = filename.split("\\.");
		if (split.length >= 2) {
			extention = split[split.length - 1];
		}

		if (root) {

			this.baseUrl = "http://" + resourceUri.getHost()
					+ resourceUri.getPath();
			split = this.baseUrl.split(filename);
			baseUrl = split[0];
			Log.d(TAG, baseUrl);

			// The current playlist-files correspond to qualities.

			String currentPath = "";
			Float currentQuality = 0f;

			String content = downloadContents(resourceUri);
			for (String line : content.split("\n")) {
				line = line.trim();
				if (line.startsWith("#") && line.contains("BANDWIDTH")) {
					// Get the current bandwidth from the EXT comment
					String[] split2 = line.split("BANDWIDTH=");
					currentQuality = Float.parseFloat(split2[1]);
				} else if (!line.startsWith("#")) {

					String[] split2 = line.split("\\/");
					currentPath = split2[0];

					this.playlistQualityUrlMap.put(currentQuality, currentPath);

					Log.d(TAG, "Put: " + currentQuality + " : " + currentPath);

					parseAndAddToList(Uri.parse(this.baseUrl + line), false);

					/*
					 * String uriPathWithoutFilename = resourceUri.toString();
					 * uriPathWithoutFilename =
					 * uriPathWithoutFilename.substring( 0,
					 * uriPathWithoutFilename.indexOf(resourceUri
					 * .getLastPathSegment()));
					 */

					/*
					 * Uri child = Uri.withAppendedPath(
					 * Uri.parse(uriPathWithoutFilename), line.trim());
					 */
				}
			}

			/*
			 * for (Uri uri : parseList(resourceUri)) {
			 * this.parseAndAddToList(uri, false); }
			 */
		} else {
			Log.d(TAG, "List of files from: " + resourceUri.toString());
			String content = downloadContents(resourceUri);
			for (String line : content.split("\n")) {
				line = line.trim();
				if (!line.startsWith("#")) {
					this.videoFileNames.add(line);
					Log.d(TAG, "File: " + line);
				}
			}
		}
	}

	/*
	 * private Vector<Uri> parseList(Uri listUri) throws Exception { Vector<Uri>
	 * ret = new Vector<Uri>();
	 * 
	 * String content = downloadContents(listUri);
	 * 
	 * // Log.d(TAG, content);
	 * 
	 * for (String line : content.split("\n")) {
	 * 
	 * // Log.d(TAG, "Line: " + line);
	 * 
	 * if (line.trim().startsWith("#")) { continue; }
	 * 
	 * String uriPathWithoutFilename = listUri.toString();
	 * uriPathWithoutFilename = uriPathWithoutFilename .substring(0,
	 * uriPathWithoutFilename.indexOf(listUri .getLastPathSegment()));
	 * 
	 * Uri child = Uri.withAppendedPath(Uri.parse(uriPathWithoutFilename),
	 * line.trim()); ret.add(child); }
	 * 
	 * return ret; }
	 */

	private String downloadContents(Uri uri) throws Exception {
		StringBuilder sb = new StringBuilder();
		HttpClient httpClient = new DefaultHttpClient();
		HttpContext localContext = new BasicHttpContext();
		HttpGet httpGet = new HttpGet(new URI(uri.toString()));
		HttpResponse response = httpClient.execute(httpGet, localContext);

		BufferedReader reader = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent()));

		String line = null;
		while ((line = reader.readLine()) != null) {
			sb.append(line + "\n");
		}

		return sb.toString();
	}

	@Override
	public void setUrl(String topPlaylistUrl) throws Exception {
		this.fullUrl = topPlaylistUrl;

		parseAndAddToList(Uri.parse(topPlaylistUrl), true);

		startBuffering();
	}

	private void startBuffering() {
		for (int i = 0; i < CONF_BUFFER_SIZE; i++) {
			// Listen for ready-state of the first movie (to tell the player)
			boolean listenCallback = i == 0;
			startCacheingVideo(i, listenCallback);
			this.currentCachedVideoFile++;
		}
	}

	private void startCacheingVideo(int index, boolean callback) {
		BufferedVideoFile video = new BufferedVideoFile();

		Vector<Float> quals = new Vector<Float>(
				this.playlistQualityUrlMap.keySet());

		String videoUrl = this.baseUrl
				+ this.playlistQualityUrlMap
						.get(quals.get(this.currentQuality)) + "/"
				+ this.videoFileNames.get(this.currentCachedVideoFile);

		Log.d(TAG, "Downloading: " + videoUrl);

		if (callback) {
			video.setReadyListener(this);
		}
		video.downloadAsync(videoUrl);
		this.bufferedVideoParts.add(video);
	}

	@Override
	public String getUrl() {
		return this.fullUrl;
	}

	@Override
	public boolean validateUrl(String topPlaylistUrl) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Vector<Float> getAvailableQualities() {

		Vector<Float> list = new Vector<Float>(
				this.playlistQualityUrlMap.keySet());
		return list;
	}

	@Override
	public void setQuality(int qualityIndex) {
		if (qualityIndex == this.currentQuality) {
			return;
		}
		this.currentQuality = qualityIndex;

		/*
		 * for (int i = this.bufferedVideoParts.size() - 1; i >
		 * this.currentPlayingVideoFile + 1; i--) {
		 * this.bufferedVideoParts.remove(i); this.currentCachedVideoFile--; }
		 */

		this.listener.preparingForQualityChange();
		this.bufferedVideoParts.clear();

		this.qualityChangeFetchMillisecondOffset += this.listener
				.currentlyPlayedMilliseconds();
		this.currentPlayingVideoFile = this.currentCachedVideoFile = (this.listener
				.currentlyPlayedMilliseconds() / 10000)
				+ (this.qualityChangeFetchMillisecondOffset / 10000);

		this.qualityChangeMillisecondOffset = this.listener
				.currentlyPlayedMilliseconds() % 10000;

		startCacheingVideo(++this.currentCachedVideoFile, true);
		startCacheingVideo(++this.currentCachedVideoFile, false);
	}

	@Override
	public boolean hasNextLocalVideoUrl() {
		return true;
	}

	@Override
	public String getNextLocalVideoUrl() {

		// this.startCacheingVideo(++this.currentCachedVideoFile, false);
		return "http://127.0.0.1:" + this.listenPort + "/video.ts";
	}

	@Override
	public void fileIsNowReady(BufferedVideoFile file) {
		// The first video file is now downloaded.
		// Tell player we are ready.
		this.listener.readyForPlaybackNow(this.qualityChangeMillisecondOffset);
	}
}
