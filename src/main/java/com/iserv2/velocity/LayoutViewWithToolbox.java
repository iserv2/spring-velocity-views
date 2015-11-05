package com.iserv2.velocity;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.tools.ToolManager;
import org.apache.velocity.tools.view.ViewToolContext;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.servlet.view.velocity.VelocityLayoutView;
import org.springframework.web.util.NestedServletException;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LayoutViewWithToolbox extends VelocityLayoutView {
	public static final String APPLICATION_MACRO_LIB = "_view-macros.vm";
	public static final String APPLICATION_CONTEXT_KEY = "applicationContext";

	private ToolManager toolManager;
	private String layoutKeyCopy = DEFAULT_LAYOUT_KEY;
	private String layoutUrlCopy = DEFAULT_LAYOUT_URL;
	private String screenContentKeyCopy = DEFAULT_SCREEN_CONTENT_KEY;

	@Override
	public void setLayoutKey(String layoutKey) {
		this.layoutKeyCopy = layoutKey;
		super.setLayoutKey(layoutKey);
	}

	@Override
	public void setLayoutUrl(String layoutUrl) {
		this.layoutUrlCopy = layoutUrl;
		super.setLayoutUrl(layoutUrl);
	}

	@Override
	public void setScreenContentKey(String screenContentKey) {
		this.screenContentKeyCopy = screenContentKey;
		super.setScreenContentKey(screenContentKey);
	}

	@PostConstruct
	public void createToolManager() {
		if (getToolboxConfigLocation() == null) {
			return;
		}
		toolManager = new ToolManager();
		toolManager.configure(getToolboxConfigLocation());
	}

	public static List<String> viewMacroLibraries() {
		return new ArrayList<>(Collections.singletonList(APPLICATION_MACRO_LIB));
	}

	@Override
	protected VelocityContext createVelocityContext(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) {
		ViewToolContext toolContext = new ViewToolContext(getVelocityEngine(), request, response, getServletContext());
		toolContext.putToolProperty(ViewToolContext.LOCALE_KEY, RequestContextUtils.getLocale(request));
		toolContext.putToolProperty(APPLICATION_CONTEXT_KEY, RequestContextUtils.findWebApplicationContext(request));

		// Load a Velocity Tools toolbox, if necessary.
		if (toolManager != null) {
			toolContext.addToolbox(toolManager.getToolboxFactory().createToolbox("application"));
			toolContext.addToolbox(toolManager.getToolboxFactory().createToolbox("request"));
		}
		VelocityContext velocityContext = new VelocityContext(model, toolContext);
		velocityContext.setMacroLibraries(viewMacroLibraries());
		return velocityContext;
	}

	/**
	 * We override parent method, because we want to use macro libraries
	 */
	@Override
	protected void doRender(Context context, HttpServletResponse response) throws Exception {
		renderScreenContent(context);

		// Velocity context now includes any mappings that were defined
		// (via #set) in screen content template.
		// The screen template can overrule the layout by doing
		// #set( $layout = "MyLayout.vm" )
		String layoutUrlToUse = (String) context.get(layoutKeyCopy);
		if (layoutUrlToUse != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Screen content template has requested layout [" + layoutUrlToUse + "]");
			}
		} else {
			// No explicit layout URL given -> use default layout of this view.
			layoutUrlToUse = this.layoutUrlCopy;
		}

		mergeTemplate(getTemplate(layoutUrlToUse), context, response);
	}

	private void renderScreenContent(Context velocityContext) throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Rendering screen content template [" + getUrl() + "]");
		}

		StringWriter sw = new StringWriter();
		Template screenContentTemplate = getTemplate(getUrl());
		screenContentTemplate.merge(velocityContext, sw, getContextMacroLibraries(velocityContext));

		// Put rendered content into Velocity context.
		velocityContext.put(screenContentKeyCopy, sw.toString());
	}

	@Override
	protected void mergeTemplate(Template template, Context context, HttpServletResponse response) throws Exception {
		try {
			template.merge(context, response.getWriter(), getContextMacroLibraries(context));
		} catch (MethodInvocationException ex) {
			Throwable cause = ex.getWrappedThrowable();
			throw new NestedServletException(
				"Method invocation failed during rendering of Velocity view with name '" + getBeanName() + "': " + ex.getMessage() + "; reference [" + ex.getReferenceName() + "], method '" + ex.getMethodName() + "'",
				cause == null ? ex : cause);
		}
	}

	private static List getContextMacroLibraries(Context velocityContext) {
		if (velocityContext instanceof VelocityContext) {
			return ((VelocityContext) velocityContext).getMacroLibraries();
		}
		return null;
	}
}
