/* Include.java

	Purpose:
		
	Description:
		
	History:
		Wed Sep 28 18:01:03     2005, Created by tomyeh

Copyright (C) 2005 Potix Corporation. All Rights Reserved.

	This program is distributed under GPL Version 2.0 in the hope that
	it will be useful, but WITHOUT ANY WARRANTY.
*/
package org.zkoss.zul;

import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.io.Writer;
import java.io.StringWriter;
import java.io.IOException;

import org.zkoss.lang.Objects;
import org.zkoss.lang.Strings;
import org.zkoss.lang.Exceptions;
import org.zkoss.mesg.Messages;
import org.zkoss.io.Files;
import org.zkoss.util.logging.Log;

import org.zkoss.web.Attributes;

import org.zkoss.zk.mesg.MZk;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.UiException;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.sys.UiEngine;
import org.zkoss.zk.ui.sys.WebAppCtrl;
import org.zkoss.zk.ui.sys.ExecutionCtrl;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zk.ui.ext.DynamicPropertied;
import org.zkoss.zk.ui.ext.Includer;

import org.zkoss.zul.impl.XulElement;
import org.zkoss.zul.mesg.MZul;

/**
 * Includes the result generated by any servlet, not limited to a ZUML page.
 *
 * <p>Non-XUL extension.
 *
 * <p>If the servlet is eventually another ZUML page, the page will be
 * added to the current desktop in the rendering phase.
 *
 * <p>There are two ways to pass parameters to the included page.
 * First, you can use the query string:
 * <pre><code>&lt;include src="/WEB-INF/mypage?arg=something"/&gt;</code></pre>
 *
 * <p>Second, since ZK 3.0.4,
 * you can use {@link #setDynamicProperty}, or, in ZUL,
 * <pre><code>&lt;include src="/WEB-INF/mypage" arg="something"/&gt;</code></pre>
 *
 * <p>With the query string, you can pass only the String values.
 * and the parameter can be accessed by {@link Execution#getParameter}
 * or javax.servlet.ServletRequest's getParameter.
 * Or, you can access it with the param variable in EL expressions.
 *
 * <p>On the other hand, the dynamic properties ({@link #setDynamicProperty})
 * are passed to the included page thru the request's attributes.
 * You can pass any type of objects you want.
 * In the included page, you can access them by use of
 * {@link Execution#getAttribute} or javax.servlet.ServletRequest's
 * getAttribute. Or, you can access with the requestScope variable
 * in EL expressions.
 *
 * <h3>Macro Component versus {@link Include}</h3>
 *
 * <ol>
 * <li>{@link Include} could include anything include ZUML, JSP or any other
 * servlet, while a macro component could embed only a ZUML page.</li>
 * <li>If {@link Include} includes a ZUML page, a
 * {@link Page} instance is created as a child
 * of {@link Include}. On the other hand, a macro component makes
 * the created components as the direct children -- i.e.,
 * you can browse them with {@link org.zkoss.zk.ui.Component#getChildren}.</li>
 * <li>{@link Include} creates components in the Rendering phase,
 * while a macro component creates components in {@link org.zkoss.zk.ui.HtmlMacroComponent#afterCompose}.</li>
 * <li>{@link Include#invalidate} will cause it to re-include
 * the page (and then recreate the page if it includes a ZUML page).
 * However, {@link org.zkoss.zk.ui.HtmlMacroComponent#invalidate} just causes it to redraw
 * and update the content at the client -- like any other component does.
 To re-create, you have to invoke {@link org.zkoss.zk.ui.HtmlMacroComponent#recreate}.</li>
 * </ol>
 *
 * <p>In additions to macro and {@link Include}, you can use the fulfill
 * attribute as follows:
 * <code>&lt;div fulfill="=/my/foo.zul"&gt;...&lt;/div&gt;
 *
 * @author tomyeh
 * @see Iframe
 */
public class Include extends XulElement
implements DynamicPropertied, org.zkoss.zul.api.Include, Includer {
	private static final Log log = Log.lookup(Include.class);
	private String _src;
	private Map _dynams;
	/** The child page. Note: it is recovered by PageImpl. */
	private transient Page _childpg;
	private boolean _localized;
	private boolean _progressing;
	/** 0: not yet handled, 1: wait for echoEvent, 2: done. */
	private byte _progressStatus;

	public Include() {
	}
	public Include(String src) {
		setSrc(src);
	}

	/**
	 * Sets whether to show the {@link MZul#PLEASE_WAIT} message before a long operation.
	 * This implementation will automatically use an echo event like {@link Events#echoEvent(String, org.zkoss.zk.ui.Component, String)} 
	 * to suspend the including progress before using the {@link Clients#showBusy(String, boolean)} 
	 * method to show the {@link MZul#PLEASE_WAIT} message at client side. 
	 * 
	 * <p>Default: false.
	 * @since 3.0.4
	 */
	public void setProgressing(boolean progressing) {
		if (_progressing != progressing) {
			_progressing = progressing;
			checkProgressing();
		}
	}
	/**
	 * Returns whether to show the {@link MZul#PLEASE_WAIT} message before a long operation.
	 * <p>Default: false.
	 * @since 3.0.4
	 */
	public boolean getProgressing() {
		return _progressing;
	}
	/**
	 * Internal use only.
	 *@since 3.0.4
	 */
	public void onEchoInclude() {
		Clients.showBusy(null , false);
		super.invalidate();
	}
	/** Returns the src.
	 * <p>Default: null.
	 */
	public String getSrc() {
		return _src;
	}
	/** Sets the src.
	 *
	 * <p>If src is changed, the whole component is invalidate.
	 * Thus, you want to smart-update, you have to override this method.
	 *
	 * @param src the source URI. If null or empty, nothing is included.
	 * You can specify the source URI with the query string and they
	 * will become a parameter that can be accessed by use
	 * of {@link Execution#getParameter} or javax.servlet.ServletRequest's getParameter.
	 * For example, if "/a.zul?b=c" is specified, you can access
	 * the parameter with ${param.b} in a.zul.
	 * @see #setDynamicProperty
	 */
	public void setSrc(String src) throws WrongValueException {
		if (src != null && src.length() == 0) src = null;

		if (!Objects.equals(_src, src)) {
			_src = src;
			invalidate();
		}
	}
	
	/** Returns whether the source depends on the current Locale.
	 * If true, it will search xxx_en_US.yyy, xxx_en.yyy and xxx.yyy
	 * for the proper content, where src is assumed to be xxx.yyy.
	 *
	 * <p>Default: false;
	 */
	public final boolean isLocalized() {
		return _localized;
	}
	/** Sets whether the source depends on the current Locale.
	 */
	public final void setLocalized(boolean localized) {
		if (_localized != localized) {
			_localized = localized;
			invalidate();
		}
	}

	//Includer//
	public Page getChildPage() {
		return _childpg;
	}
	public void setChildPage(Page page) {
		_childpg = page;
	}

	//DynamicPropertied//
	/** Returns whether a dynamic property is defined.
	 */
	public boolean hasDynamicProperty(String name) {
		return _dynams != null && _dynams.containsKey(name);
	}
	/** Returns the parameter associated with the specified name,
	 * or null if not found.
	 *
	 * @since 3.0.4
	 * @see #setDynamicProperty
	 */
	public Object getDynamicProperty(String name) {
		return _dynams != null ? _dynams.get(name): null;
	}
	/** Adds a dynamic property that will be passed to the included page
	 * via the request's attribute.
	 *
	 * <p>For example, if setDynamicProperty("abc", new Integer(4)) is called,
	 * then the included page can retrived the abc property
	 * by use of <code>${reqestScope.abc}</code>
	 *
	 * @since 3.0.4
	 */
	public void setDynamicProperty(String name, Object value) {
		if (_dynams == null)
			_dynams = new HashMap();
		_dynams.put(name, value);
	}

	//-- Component --//
	public void invalidate() {
		super.invalidate();

		if (_progressStatus >= 2) _progressStatus = 0;
		checkProgressing();
	}
	/** Checks if _progressingg is defined.
	 */
	private void checkProgressing() {
		if(_progressing && _progressStatus == 0) {
			_progressStatus = 1;
			Clients.showBusy(Messages.get(MZul.PLEASE_WAIT), true);
			Events.echoEvent("onEchoInclude", this, null);
		}
	}

	/** Default: not childable.
	 */
	protected boolean isChildable() {
		return false;
	}
	protected void redrawChildren(Writer out) throws IOException {
		final UiEngine ueng =
			((WebAppCtrl)getDesktop().getWebApp()).getUiEngine();
		Component old = ueng.setOwner(this);
		try {
			if (_progressStatus == 1) {
				_progressStatus = 2;
			} else if (_src != null && _src.length() > 0) {
				final StringWriter sw = new StringWriter();
				include(sw);
				if (getChildPage() != null) {
					Files.write(out, sw.getBuffer());
				} else { //not ZUL page, so it must be HTML fragment
					final Writer extout =
					((ExecutionCtrl)getDesktop().getExecution())
						.getVisualizer().getExtraWriter();
					if (extout != null) {
						extout.write("<span id=\"");
						extout.write(getUuid());
						extout.write("\">");
						Files.write(extout, sw.getBuffer());
						extout.write("</span>");
					} else {
						out.write("zkm.top().props.content='");
						final StringBuffer sb = new StringBuffer(1024);
						Strings.escape(sb, sw.getBuffer(), "'\\\n\r\t\f");
						Files.write(out, sb);
						out.write("';");
					}
				}
			}
		} finally {
			ueng.setOwner(old);
		}
	}
	private void include(Writer out) throws IOException {
		final Desktop desktop = getDesktop();
		final Execution exec = desktop.getExecution();
		final String src = exec.toAbsoluteURI(_src, false);
		final Map old = setupDynams(exec);
		try {
			exec.include(out, src, null, 0);
		} catch (Throwable err) {
		//though DHtmlLayoutServlet handles exception, we still have to
		//handle it because src might not be ZUML
			final String errpg =
				desktop.getWebApp().getConfiguration().getErrorPage(
					desktop.getDeviceType(), err);
			if (errpg != null) {
				try {
					exec.setAttribute("javax.servlet.error.message", Exceptions.getMessage(err));
					exec.setAttribute("javax.servlet.error.exception", err);
					exec.setAttribute("javax.servlet.error.exception_type", err.getClass());
					exec.setAttribute("javax.servlet.error.status_code", new Integer(500));
					exec.include(out, errpg, null, 0);
					return; //done
				} catch (IOException ex) { //eat it (connection off)
				} catch (Throwable ex) {
					log.warning("Failed to load the error page: "+errpg, ex);
				}
			}

			final String msg = Messages.get(MZk.PAGE_FAILED,
				new Object[] {src, Exceptions.getMessage(err),
					Exceptions.formatStackTrace(null, err, null, 6)});
			final HashMap attrs = new HashMap();
			attrs.put(Attributes.ALERT_TYPE, "error");
			attrs.put(Attributes.ALERT, msg);
			exec.include(out,
				"~./html/alert.dsp", attrs, Execution.PASS_THRU_ATTR);
		} finally {
			restoreDynams(exec, old);
		}
	}
	private Map setupDynams(Execution exec) {
		if (_dynams == null || _dynams.isEmpty())
			return null;

		final Map old = new HashMap();
		for (Iterator it = _dynams.entrySet().iterator(); it.hasNext();) {
			final Map.Entry me = (Map.Entry)it.next();
			final String nm = (String)me.getKey();
			final Object val = me.getValue();

			old.put(nm, exec.getAttribute(nm));

			if (val != null) exec.setAttribute(nm, val);
			else exec.removeAttribute(nm);
		}
		return old;
	}
	private static void restoreDynams(Execution exec, Map old) {
		if (old != null)
			for (Iterator it = old.entrySet().iterator(); it.hasNext();) {
				final Map.Entry me = (Map.Entry)it.next();
				final String nm = (String)me.getKey();
				final Object val = me.getValue();

				if (val != null) exec.setAttribute(nm, val);
				else exec.removeAttribute(nm);
			}
	}
}
