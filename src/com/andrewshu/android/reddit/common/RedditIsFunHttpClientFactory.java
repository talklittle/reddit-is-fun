package com.andrewshu.android.reddit.common;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import com.andrewshu.android.reddit.R;
import com.andrewshu.android.reddit.RedditIsFunApplication;

public class RedditIsFunHttpClientFactory {
	
	private static final String TAG = "RedditIsFunHttpClientFactory";
	
	private static final DefaultHttpClient mGzipHttpClient = createGzipHttpClient();
	private static final CookieStore mCookieStore = mGzipHttpClient.getCookieStore();

	// Default connection and socket timeout of 60 seconds.  Tweak to taste.
	private static final int SOCKET_OPERATION_TIMEOUT = 60 * 1000;

	static DefaultHttpClient createGzipHttpClient() {
		HttpParams params = new BasicHttpParams();
		params.setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);
		
		DefaultHttpClient httpclient = new DefaultHttpClient(params) {
		    @Override
		    protected ClientConnectionManager createClientConnectionManager() {
		        SchemeRegistry registry = new SchemeRegistry();
		        registry.register(
		                new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		        registry.register(
		        		new Scheme("https", getHttpsSocketFactory(), 443));
		        HttpParams params = getParams();
				HttpConnectionParams.setConnectionTimeout(params, SOCKET_OPERATION_TIMEOUT);
				HttpConnectionParams.setSoTimeout(params, SOCKET_OPERATION_TIMEOUT);
				HttpConnectionParams.setSocketBufferSize(params, 8192);
		        return new ThreadSafeClientConnManager(params, registry);
		    }
		    
		    /** Gets an HTTPS socket factory with SSL Session Caching if such support is available, otherwise falls back to a non-caching factory
		     * @return
		     */
		    protected SocketFactory getHttpsSocketFactory(){
				try {
					Class<?> sslSessionCacheClass = Class.forName("android.net.SSLSessionCache");
			    	Object sslSessionCache = sslSessionCacheClass.getConstructor(Context.class).newInstance(RedditIsFunApplication.getApplication());
			    	Method getHttpSocketFactory = Class.forName("android.net.SSLCertificateSocketFactory").getMethod("getHttpSocketFactory", new Class<?>[]{int.class, sslSessionCacheClass});
			    	return (SocketFactory) getHttpSocketFactory.invoke(null, SOCKET_OPERATION_TIMEOUT, sslSessionCache);
				}catch(Exception e){
					return SSLSocketFactory.getSocketFactory();
				}
		    }
		};
		
		
        httpclient.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(
                    final HttpRequest request,
                    final HttpContext context
            ) throws HttpException, IOException {
            	RedditIsFunApplication app = RedditIsFunApplication.getApplication();
            	String version;
				try {
					version = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
				} catch (NameNotFoundException e) {
					Log.e(TAG, "Package name not found.", e);
					version = "1";
				}
            	String userAgent = app.getString(R.string.user_agent, version);
                request.setHeader("User-Agent", userAgent);
            	
                if (!request.containsHeader("Accept-Encoding"))
                    request.addHeader("Accept-Encoding", "gzip");
            }
        });
        httpclient.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(
                    final HttpResponse response, 
                    final HttpContext context) throws HttpException, IOException {
                HttpEntity entity = response.getEntity();
                Header ceheader = entity.getContentEncoding();
                if (ceheader != null) {
                    HeaderElement[] codecs = ceheader.getElements();
                    for (int i = 0; i < codecs.length; i++) {
                        if (codecs[i].getName().equalsIgnoreCase("gzip")) {
                            response.setEntity(
                                    new GzipDecompressingEntity(response.getEntity())); 
                            return;
                        }
                    }
                }
            }
        });
        return httpclient;
	}
    static class GzipDecompressingEntity extends HttpEntityWrapper {
        public GzipDecompressingEntity(final HttpEntity entity) {
            super(entity);
        }
        @Override
        public InputStream getContent()
            throws IOException, IllegalStateException {
            // the wrapped entity's getContent() decides about repeatability
            InputStream wrappedin = wrappedEntity.getContent();
            return new GZIPInputStream(wrappedin);
        }
        @Override
        public long getContentLength() {
            // length of ungzipped content is not known
            return -1;
        }
    }
	/**
	 * http://hc.apache.org/httpcomponents-client/examples.html
	 * @return a Gzip-enabled DefaultHttpClient
	 */
	public static HttpClient getGzipHttpClient() {
		return mGzipHttpClient;
	}
	public static CookieStore getCookieStore() {
		return mCookieStore;
	}

}
