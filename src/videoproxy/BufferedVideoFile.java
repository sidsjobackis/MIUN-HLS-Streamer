package videoproxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.Executors;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import android.os.Environment;
import android.util.Log;

public class BufferedVideoFile {

	public boolean isReady = false;
	private String TAG = this.getClass().getSimpleName();

	private final String bufferBase = Environment
			.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
			.getAbsolutePath()
			+ "/";
	private File videoFile;
	private BufferedVideoFileEventListener listener = null;

	public BufferedVideoFile() {
	}

	public void setReadyListener(BufferedVideoFileEventListener listener) {
		this.listener = listener;
	}

	public void downloadAsync(final String url) {
		Executors.newSingleThreadExecutor().submit(new Runnable() {

			@Override
			public void run() {
				try {
					downloadSync(url);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void downloadSync(String url) throws Exception {
		String filename = "buffer_video_" + Math.abs((new Random()).nextInt())
				+ ".ts";

		Log.d(TAG, "Creating " + bufferBase + filename);

		videoFile = new File(bufferBase, filename);
		videoFile.getParentFile().mkdirs();
		videoFile.createNewFile();

		URL downloadUrl = new URL(url);
		HttpURLConnection urlConnection = (HttpURLConnection) downloadUrl
				.openConnection();
		urlConnection.setRequestMethod("GET");
		urlConnection.setDoOutput(true);
		urlConnection.connect();

		InputStream is = urlConnection.getInputStream();
		OutputStream os = new FileOutputStream(this.videoFile);

		byte[] buffer = new byte[2048];
		int size;
		while ((size = is.read(buffer)) != -1) {
			os.write(buffer, 0, size);
		}
		os.flush();

		videoFile.deleteOnExit();
		this.isReady = true;
		if (this.listener != null) {
			this.listener.fileIsNowReady(this);
		}
	}

	void delete() {
		videoFile.delete();
	}

	public void streamTo(OutputStream os) throws Exception {

		byte[] buffer = new byte[2048];
		InputStream is = new FileInputStream(this.videoFile);
		int size;
		while ((size = is.read(buffer)) != -1) {
			os.write(buffer, 0, size);
			os.flush();
		}
	}
}