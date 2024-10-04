import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;


public class EchoServer {

	public static void main(String[] args) throws Exception {
			int port = 8000;
			if( args.length > 0 ) port = Integer.parseInt(args[0]);
			
	        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
	        server.createContext("/test", new MyHandler());
	        server.setExecutor(null); // creates a default executor
	        server.start();
	    }

	    static class MyHandler implements HttpHandler {
	        public void handle(HttpExchange t) throws IOException {
	        	
	            // dump the request to stdout
	            System.out.println( "Request URI: " + t.getRequestURI().toString());
	            
	            InputStream is = t.getRequestBody();
	            byte[] buf = new byte[1000];
	            int readBytes=0;
	            do {
	            	readBytes = is.read( buf );
	            	System.out.write(buf);
	            } while( readBytes >=0 );
	            
	            System.out.flush();
	            System.out.println();
	            String response = "";
	            t.sendResponseHeaders(200, response.length());
	            OutputStream os = t.getResponseBody();
	            os.write(response.getBytes());
	            os.close();
	        }
	    }
}
