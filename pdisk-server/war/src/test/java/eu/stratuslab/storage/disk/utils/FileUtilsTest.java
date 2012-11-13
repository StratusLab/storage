package eu.stratuslab.storage.disk.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.io.IOException;

import org.junit.Test;
import org.restlet.resource.ResourceException;

public class FileUtilsTest {

    @Test
    public void closeIgnoringErrorNullOk() {
        FileUtils.closeIgnoringError(null); // no exception
    }

    @Test
    public void closeIgnoringErrorIsCalled() {
        CloseableTester c = new CloseableTester(false);
        FileUtils.closeIgnoringError(c);
        assertTrue("close() not called on closeable", c.wasClosed());
    }

    @Test
    public void closeIgnoringErrorIsCalledWithException() {
        CloseableTester c = new CloseableTester(true);
        boolean exceptionRaised = false;
        try {
            FileUtils.closeIgnoringError(c);
        } catch (Exception e) {
            exceptionRaised = true;
        }
        assertFalse("exception was raised in close", exceptionRaised);
        assertTrue("close() not called on closeable", c.wasClosed());
    }

    @Test
    public void closeRaisingErrorNullOk() {
        FileUtils.closeRaisingError(null, "dummy filename"); // no exception
    }

    @Test
    public void closeRaisingErrorIsCalled() {
        CloseableTester c = new CloseableTester(false);
        FileUtils.closeRaisingError(c, "dummmy filename");
        assertTrue("close() not called on closeable", c.wasClosed());
    }

    @Test
    public void closeRaisingErrorIsCalledWithException() {
        CloseableTester c = new CloseableTester(true);
        boolean exceptionRaised = false;
        try {
            FileUtils.closeRaisingError(c, "dummy filename");
        } catch (ResourceException e) {
            exceptionRaised = true;
        }
        assertTrue("exception not raised in close", exceptionRaised);
        assertTrue("close() not called on closeable", c.wasClosed());
    }

    private static class CloseableTester implements Closeable {

        private boolean closed = false;

        private boolean throwException = false;

        public CloseableTester(boolean throwException) {
            this.throwException = throwException;
        }

        public void close() throws IOException {
            closed = true;
            if (throwException) {
                throw new IOException("exception on close");
            }
        }

        public boolean wasClosed() {
            return closed;
        }

    }

}
