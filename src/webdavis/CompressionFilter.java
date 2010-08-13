package webdavis;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/**
 * Implements GZIP and Deflate compression filters for clients that support
 * compression.
 *
 * @author Eric Glass
 */ 
public class CompressionFilter implements Filter {

    private static final String DEFAULT_METHODS = "GET LOCK POST PROPFIND";

    private static final int GZIP_COMPRESSION = 0;

    private static final int DEFLATE_COMPRESSION = 1;

    private final Set methods = new HashSet();

    private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

    private boolean gzipEnabled = true;

    private boolean deflateEnabled = true;

    public void init(FilterConfig config) throws ServletException {
//@TBD Move these params into config file?
        String compressionLevel = config.getInitParameter("compressionLevel");
        if (compressionLevel != null) {
            this.compressionLevel = Integer.parseInt(compressionLevel);
        }
        String gzipEnabled = config.getInitParameter("gzipEnabled");
        if (gzipEnabled != null) {
            this.gzipEnabled = Boolean.valueOf(gzipEnabled).booleanValue();
        }
        String deflateEnabled = config.getInitParameter("deflateEnabled");
        if (deflateEnabled != null) {
            this.deflateEnabled =
                    Boolean.valueOf(deflateEnabled).booleanValue();
        }
        String methods = config.getInitParameter("methods");
        if (methods == null) methods = DEFAULT_METHODS;
        StringTokenizer tokenizer = new StringTokenizer(methods);
        while (tokenizer.hasMoreTokens()) {
            this.methods.add(tokenizer.nextToken().toUpperCase());
        }
    }

    public void destroy() { }

    /**
     * Filters the response, applying GZIP or Deflate compression as
     * supported by the client.  By default, this will be applied to
     * GET, LOCK, POST, and PROPFIND response bodies when support for
     * compression is indicated by the client.
     *
     * @param req The request.
     * @param resp The response.
     * @param chain The filter chain.
     *
     * @throws IOException If an IO error occurs.
     * @throws ServletException If an application error is encountered.
     */
    public void doFilter(ServletRequest req, ServletResponse resp,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        String method = request.getMethod().toUpperCase();
        if (!methods.contains(method)) {
            chain.doFilter(request, response);
            return;
        }
        Enumeration encodings = request.getHeaders("Accept-Encoding");
        if (encodings != null) {
            float identityWeight = -1.0f;
            float deflateWeight = -1.0f;
            float gzipWeight = -1.0f;
            float undefinedWeight = -1.0f;
            while (encodings.hasMoreElements()) {
                StringTokenizer tokenizer = new StringTokenizer(
                        (String) encodings.nextElement(), ",");
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken().trim();
                    int index = token.indexOf(';');
                    String encoding = (index != -1) ?
                            token.substring(0, index).trim() : token;
                    float q = (index != -1) ? Float.parseFloat(
                            token.substring(index + 1).trim()) : 0.5f;
                    if ("gzip".equalsIgnoreCase(encoding)) {
                        gzipWeight = q;
                    } else if ("deflate".equalsIgnoreCase(encoding)) {
                        deflateWeight = q;
                    } else if ("identity".equalsIgnoreCase(encoding)) {
                        identityWeight = q;
                    } else if ("*".equalsIgnoreCase(encoding)) {
                        undefinedWeight = q;
                    }
                }
            }
            if (undefinedWeight != -1.0f) {
                if (identityWeight == -1.0f) identityWeight = undefinedWeight;
                if (deflateWeight == -1.0f) deflateWeight = undefinedWeight;
                if (gzipWeight == -1.0f) gzipWeight = undefinedWeight;
            }
            if (gzipWeight > 0.0f) {
                if (gzipWeight >= deflateWeight &&
                        gzipWeight >= identityWeight) {
                    response = new CompressedResponse(response,
                            GZIP_COMPRESSION);
                } else if (deflateWeight >= identityWeight) {
                    response = new CompressedResponse(response,
                            DEFLATE_COMPRESSION);
                }
            } else if (deflateWeight > 0.0f) {
                if (deflateWeight >= gzipWeight &&
                        deflateWeight >= identityWeight) {
                    response = new CompressedResponse(response,
                            DEFLATE_COMPRESSION);
                } else if (gzipWeight >= identityWeight) {
                    response = new CompressedResponse(response,
                            GZIP_COMPRESSION);
                }
            } else if (identityWeight == 0.0f) {
                response.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE);
                return;
            }
            chain.doFilter(request, response);
            if (response instanceof CompressedResponse) {
                ((CompressedResponse) response).finish();
            }
        }
    }

    private class CompressedResponse extends HttpServletResponseWrapper {

        private ServletOutputStream output;

        private PrintWriter writer;

        private final int type;

        public CompressedResponse(HttpServletResponse response, int type) {
            super(response);
            this.type = type;
            if (type == GZIP_COMPRESSION) {
                response.setHeader("Content-Encoding", "gzip");
            } else if (type == DEFLATE_COMPRESSION) {
                response.setHeader("Content-Encoding", "deflate");
            }
        }

        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter already called.");
            }
            return (output != null) ? output : (output = wrapOutputStream());
        }

        public PrintWriter getWriter() throws IOException {
            if (writer != null) return writer;
            if (output != null) {
                throw new IllegalStateException(
                        "getOutputStream already called.");
            }
            return (writer = new PrintWriter(new OutputStreamWriter(
                    output = wrapOutputStream(), getCharacterEncoding())));
        }

        public void addHeader(String header, String value) {
            if (!"Content-Length".equalsIgnoreCase(header)) {
                super.addHeader(header, value);
            }
        }

        public void addIntHeader(String header, int value) {
            if (!"Content-Length".equalsIgnoreCase(header)) {
                super.addIntHeader(header, value);
            }
        }

        public void setHeader(String header, String value) {
            if (!"Content-Length".equalsIgnoreCase(header)) {
                super.setHeader(header, value);
            }
        }

        public void setIntHeader(String header, int value) {
            if (!"Content-Length".equalsIgnoreCase(header)) {
                super.setIntHeader(header, value);
            }
        }

        public void setContentLength(int contentLength) { }

        public void flushBuffer() throws IOException {
            if (writer != null) {
                writer.flush();
            } else {
                getOutputStream().flush();
            }
        }

        public void finish() throws IOException {
            if (writer != null) {
                writer.close();
            } else if (output != null) {
                output.close();
            }
        }

        private ServletOutputStream wrapOutputStream() throws IOException {
            if (type == GZIP_COMPRESSION) {
                return new WrappedOutputStream(new GZIPOutputStream(
                        super.getOutputStream()));
            } else if (type == DEFLATE_COMPRESSION) {
                return new WrappedOutputStream(new DeflaterOutputStream(
                        super.getOutputStream(),
                                new Deflater(compressionLevel, true)));
            } else {
                return super.getOutputStream();
            }
        }

    }

    private static class WrappedOutputStream extends ServletOutputStream {

        private final DeflaterOutputStream output;

        public WrappedOutputStream(DeflaterOutputStream output) {
            this.output = output;
        }

        public void close() throws IOException {
            output.finish();
            output.close();
        }

        public void flush() throws IOException {
            output.flush();
        }

        public void write(byte[] b) throws IOException {
            output.write(b);
        }

        public void write(byte[] b, int offset, int length)
                throws IOException {
            output.write(b, offset, length);
        }

        public void write(int b) throws IOException {
            output.write(b);
        }

    }

}
