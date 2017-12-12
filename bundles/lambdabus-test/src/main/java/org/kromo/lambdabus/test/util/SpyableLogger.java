package org.kromo.lambdabus.test.util;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/**
 * {@link Logger}s returned by {@link LoggerFactory} seem to be final and thus cannot be spied on.
 * This delegating non-final class to can be used to spy on logging calls.
 */
public class SpyableLogger implements Logger {
    private final Logger delegate;

    public SpyableLogger(final Logger logger) {
        delegate = Objects.requireNonNull(logger, "'logger' must not be null");
    }

    public void debug(Marker arg0, String arg1, Object arg2, Object arg3) {
        delegate.debug(arg0, arg1, arg2, arg3);
    }

    public void debug(Marker arg0, String arg1, Object... arg2) {
        delegate.debug(arg0, arg1, arg2);
    }

    public void debug(Marker arg0, String arg1, Object arg2) {
        delegate.debug(arg0, arg1, arg2);
    }

    public void debug(Marker arg0, String arg1, Throwable arg2) {
        delegate.debug(arg0, arg1, arg2);
    }

    public void debug(Marker arg0, String arg1) {
        delegate.debug(arg0, arg1);
    }

    public void debug(String arg0, Object arg1, Object arg2) {
        delegate.debug(arg0, arg1, arg2);
    }

    public void debug(String arg0, Object... arg1) {
        delegate.debug(arg0, arg1);
    }

    public void debug(String arg0, Object arg1) {
        delegate.debug(arg0, arg1);
    }

    public void debug(String arg0, Throwable arg1) {
        delegate.debug(arg0, arg1);
    }

    public void debug(String arg0) {
        delegate.debug(arg0);
    }

    public void error(Marker arg0, String arg1, Object arg2, Object arg3) {
        delegate.error(arg0, arg1, arg2, arg3);
    }

    public void error(Marker arg0, String arg1, Object... arg2) {
        delegate.error(arg0, arg1, arg2);
    }

    public void error(Marker arg0, String arg1, Object arg2) {
        delegate.error(arg0, arg1, arg2);
    }

    public void error(Marker arg0, String arg1, Throwable arg2) {
        delegate.error(arg0, arg1, arg2);
    }

    public void error(Marker arg0, String arg1) {
        delegate.error(arg0, arg1);
    }

    public void error(String arg0, Object arg1, Object arg2) {
        delegate.error(arg0, arg1, arg2);
    }

    public void error(String arg0, Object... arg1) {
        delegate.error(arg0, arg1);
    }

    public void error(String arg0, Object arg1) {
        delegate.error(arg0, arg1);
    }

    public void error(String arg0, Throwable arg1) {
        delegate.error(arg0, arg1);
    }

    public void error(String arg0) {
        delegate.error(arg0);
    }

    public String getName() {
        return delegate.getName();
    }

    public void info(Marker arg0, String arg1, Object arg2, Object arg3) {
        delegate.info(arg0, arg1, arg2, arg3);
    }

    public void info(Marker arg0, String arg1, Object... arg2) {
        delegate.info(arg0, arg1, arg2);
    }

    public void info(Marker arg0, String arg1, Object arg2) {
        delegate.info(arg0, arg1, arg2);
    }

    public void info(Marker arg0, String arg1, Throwable arg2) {
        delegate.info(arg0, arg1, arg2);
    }

    public void info(Marker arg0, String arg1) {
        delegate.info(arg0, arg1);
    }

    public void info(String arg0, Object arg1, Object arg2) {
        delegate.info(arg0, arg1, arg2);
    }

    public void info(String arg0, Object... arg1) {
        delegate.info(arg0, arg1);
    }

    public void info(String arg0, Object arg1) {
        delegate.info(arg0, arg1);
    }

    public void info(String arg0, Throwable arg1) {
        delegate.info(arg0, arg1);
    }

    public void info(String arg0) {
        delegate.info(arg0);
    }

    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    public boolean isDebugEnabled(Marker arg0) {
        return delegate.isDebugEnabled(arg0);
    }

    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    public boolean isErrorEnabled(Marker arg0) {
        return delegate.isErrorEnabled(arg0);
    }

    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    public boolean isInfoEnabled(Marker arg0) {
        return delegate.isInfoEnabled(arg0);
    }

    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    public boolean isTraceEnabled(Marker arg0) {
        return delegate.isTraceEnabled(arg0);
    }

    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    public boolean isWarnEnabled(Marker arg0) {
        return delegate.isWarnEnabled(arg0);
    }

    public void trace(Marker arg0, String arg1, Object arg2, Object arg3) {
        delegate.trace(arg0, arg1, arg2, arg3);
    }

    public void trace(Marker arg0, String arg1, Object... arg2) {
        delegate.trace(arg0, arg1, arg2);
    }

    public void trace(Marker arg0, String arg1, Object arg2) {
        delegate.trace(arg0, arg1, arg2);
    }

    public void trace(Marker arg0, String arg1, Throwable arg2) {
        delegate.trace(arg0, arg1, arg2);
    }

    public void trace(Marker arg0, String arg1) {
        delegate.trace(arg0, arg1);
    }

    public void trace(String arg0, Object arg1, Object arg2) {
        delegate.trace(arg0, arg1, arg2);
    }

    public void trace(String arg0, Object... arg1) {
        delegate.trace(arg0, arg1);
    }

    public void trace(String arg0, Object arg1) {
        delegate.trace(arg0, arg1);
    }

    public void trace(String arg0, Throwable arg1) {
        delegate.trace(arg0, arg1);
    }

    public void trace(String arg0) {
        delegate.trace(arg0);
    }

    public void warn(Marker arg0, String arg1, Object arg2, Object arg3) {
        delegate.warn(arg0, arg1, arg2, arg3);
    }

    public void warn(Marker arg0, String arg1, Object... arg2) {
        delegate.warn(arg0, arg1, arg2);
    }

    public void warn(Marker arg0, String arg1, Object arg2) {
        delegate.warn(arg0, arg1, arg2);
    }

    public void warn(Marker arg0, String arg1, Throwable arg2) {
        delegate.warn(arg0, arg1, arg2);
    }

    public void warn(Marker arg0, String arg1) {
        delegate.warn(arg0, arg1);
    }

    public void warn(String arg0, Object arg1, Object arg2) {
        delegate.warn(arg0, arg1, arg2);
    }

    public void warn(String arg0, Object... arg1) {
        delegate.warn(arg0, arg1);
    }

    public void warn(String arg0, Object arg1) {
        delegate.warn(arg0, arg1);
    }

    public void warn(String arg0, Throwable arg1) {
        delegate.warn(arg0, arg1);
    }

    public void warn(String arg0) {
        delegate.warn(arg0);
    }

}
