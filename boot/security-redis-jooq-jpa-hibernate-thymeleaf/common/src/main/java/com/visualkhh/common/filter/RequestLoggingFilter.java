package com.visualkhh.common.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Created by hanxi on 2017/9/21.
 */
public class RequestLoggingFilter extends OncePerRequestFilter implements Ordered {
	private static Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static ThreadLocal<Long> requestBeginTime = new ThreadLocal<>();
	private static final int DEFAULT_MAX_PAYLOAD_LENGTH = 1024;

	private int maxPayloadLength = DEFAULT_MAX_PAYLOAD_LENGTH;
	// Not LOWEST_PRECEDENCE, but near the end, so it has a good chance of catching all
	// enriched headers, but users can add stuff after this if they want to
	private int order = Ordered.LOWEST_PRECEDENCE - 10;

	public RequestLoggingFilter() {
		super();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		MDC.put("traceId", request.getSession().getId());

		boolean isFirstRequest = !isAsyncDispatch(request);
		HttpServletRequest requestToUse = request;

		if (isFirstRequest && !(request instanceof ContentCachingRequestWrapper)) {
			requestToUse = new ContentCachingRequestWrapper(request, getMaxPayloadLength());
		}

		HttpServletResponse responseToUse = response;
		if (!(response instanceof ContentCachingResponseWrapper)) {
			responseToUse = new ContentCachingResponseWrapper(response);
		}

		requestBeginTime.set(System.currentTimeMillis());

		try {
			filterChain.doFilter(requestToUse, responseToUse);
		} finally {
			if (!isAsyncStarted(requestToUse)) {
				logRequest(createMessage(requestToUse, responseToUse));
			}
		}

		MDC.clear();
	}

	protected String createMessage(HttpServletRequest request, HttpServletResponse resp) {
		StringBuilder msg = new StringBuilder();
		msg.append(request.getMethod());
		msg.append(" uri=").append(request.getRequestURI());

		String queryString = request.getQueryString();
		if (queryString != null) {
			msg.append('?').append(queryString);
		}


		String client = request.getRemoteAddr();
		if (StringUtils.hasLength(client)) {
			msg.append(";client=").append(client);
		}
		HttpSession session = request.getSession(false);
		if (session != null) {
			msg.append(";session=").append(session.getId());
		}
		String user = request.getRemoteUser();
		if (user != null) {
			msg.append(";user=").append(user);
		}


		msg.append(";headers=").append(new ServletServerHttpRequest(request).getHeaders());

		ContentCachingRequestWrapper wrapper =
				WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
		if (wrapper != null) {
			byte[] buf = wrapper.getContentAsByteArray();
			if (buf.length > 0) {
				int length = Math.min(buf.length, getMaxPayloadLength());
				String payload;
				try {
					payload = new String(buf, 0, length, wrapper.getCharacterEncoding());
				} catch (UnsupportedEncodingException ex) {
					payload = "[unknown]";
				}
				msg.append(";payload=").append(payload);
			}
		}

		ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(resp, ContentCachingResponseWrapper.class);
		if (responseWrapper != null) {
			byte[] buf = responseWrapper.getContentAsByteArray();
			try {
				responseWrapper.copyBodyToResponse();
			} catch (IOException e) {
				LOG.error("Fail to write response body back", e);
			}
			if (buf.length > 0) {
				String payload;
				try {
					payload = new String(buf, 0, buf.length, responseWrapper.getCharacterEncoding());
				} catch (UnsupportedEncodingException ex) {
					payload = "[unknown]";
				}
				msg.append(";response=").append(payload);
			}
		}

		return msg.toString();
	}


	public int getMaxPayloadLength() {
		return maxPayloadLength;
	}

	protected void logRequest(String message) {
		long begin = requestBeginTime.get();
		long end = System.currentTimeMillis();

		long duration = end - begin;
		LOG.info(message + ", request time:" + duration);
	}

	@Override
	public int getOrder() {
		return this.order;
	}
}