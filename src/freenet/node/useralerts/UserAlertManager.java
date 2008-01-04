/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.useralerts;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;

import freenet.support.HTMLNode;
import freenet.l10n.L10n;
import freenet.node.NodeClientCore;

/**
 * Collection of UserAlert's.
 */
public class UserAlertManager implements Comparator {

	private final HashSet alerts;
	private final NodeClientCore core;

	public UserAlertManager(NodeClientCore core) {
		this.core = core;
		alerts = new LinkedHashSet();
	}

	public void register(UserAlert alert) {
		synchronized (alerts) {
			if(!alerts.contains(alert))
				alerts.add(alert);
		}
	}

	public void unregister(UserAlert alert) {
		synchronized (alerts) {
			alerts.remove(alert);
		}
	}

	/**
	 * Tries to find the user alert with the given hash code and dismisses it,
	 * if found.
	 * 
	 * @see #unregister(UserAlert)
	 * @param alertHashCode
	 *            The hash code of the user alert to dismiss
	 */
	public void dismissAlert(int alertHashCode) {
		UserAlert[] userAlerts = getAlerts();
		for (int index = 0, count = userAlerts.length; index < count; index++) {
			UserAlert userAlert = userAlerts[index];
			if (userAlert.hashCode() == alertHashCode) {
				if (userAlert.userCanDismiss()) {
					if (userAlert.shouldUnregisterOnDismiss()) {
						userAlert.onDismiss();
						unregister(userAlert);
					} else {
						userAlert.isValid(false);
					}
				}
			}
		}
	}

	public UserAlert[] getAlerts() {
		UserAlert[] a = null;
		synchronized (alerts) {
			a = (UserAlert[]) alerts.toArray(new UserAlert[alerts.size()]);
		}
		Arrays.sort(a, this);
		return a;
	}

	public int compare(Object arg0, Object arg1) {
		UserAlert a0 = (UserAlert) arg0;
		UserAlert a1 = (UserAlert) arg1;
		if(a0 == a1) return 0; // common case, also we should be consistent with == even with proxyuseralert's
		short prio0 = a0.getPriorityClass();
		short prio1 = a1.getPriorityClass();
		if(prio0 - prio1 == 0) {
			int hash0 = a0.hashCode();
			int hash1 = a1.hashCode();
			if(hash0 > hash1) return 1;
			if(hash1 > hash0) return -1;
			return 0;
		} else {
			if(prio0 > prio1) return 1;
			else return -1;
		}
	}
	
	/**
	 * Write the alerts as HTML to a StringBuffer
	 */
	public HTMLNode createAlerts() {
		HTMLNode alertsNode = new HTMLNode("div");
		UserAlert[] alerts = getAlerts();
		int totalNumber = 0;
		for (int i = 0; i < alerts.length; i++) {
			UserAlert alert = alerts[i];
			if (!alert.isValid())
				continue;
			totalNumber++;
			alertsNode.addChild(renderAlert(alert));
		}
		if (totalNumber == 0) {
			return new HTMLNode("#", "");
		}
		return alertsNode;
	}

	/**
	 * Renders the given alert and returns the rendered HTML node.
	 * 
	 * @param userAlert
	 *            The user alert to render
	 * @return The rendered HTML node
	 */
	public HTMLNode renderAlert(UserAlert userAlert) {
		HTMLNode userAlertNode = null;
		short level = userAlert.getPriorityClass();
		if (level <= UserAlert.CRITICAL_ERROR)
			userAlertNode = new HTMLNode("div", "class", "infobox infobox-error");
		else if (level <= UserAlert.ERROR)
			userAlertNode = new HTMLNode("div", "class", "infobox infobox-alert");
		else if (level <= UserAlert.WARNING)
			userAlertNode = new HTMLNode("div", "class", "infobox infobox-warning");
		else if (level <= UserAlert.MINOR)
			userAlertNode = new HTMLNode("div", "class", "infobox infobox-information");

		assert userAlertNode != null: "user alert has invalid priority!";
		
		userAlertNode.addChild("div", "class", "infobox-header", userAlert.getTitle());
		HTMLNode alertContentNode = userAlertNode.addChild("div", "class", "infobox-content");
		alertContentNode.addChild(userAlert.getHTMLText());
		if (userAlert.userCanDismiss()) {
			HTMLNode dismissFormNode = alertContentNode.addChild("form", new String[] { "action", "method" }, new String[] { ".", "post" }).addChild("div");
			dismissFormNode.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "disable", String.valueOf(userAlert.hashCode()) });
			dismissFormNode.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "formPassword", core.formPassword });
			dismissFormNode.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "dismiss-user-alert", userAlert.dismissButtonText() });
		}
		return userAlertNode;
	}

	/**
	 * Write the alert summary as HTML to a StringBuffer
	 */
	public HTMLNode createSummary() {
		short highestLevel = 99;
		int numberOfCriticalError = 0;
		int numberOfError = 0;
		int numberOfWarning = 0;
		int numberOfMinor = 0;
		int totalNumber = 0;
		UserAlert[] alerts = getAlerts();
		for (int i = 0; i < alerts.length; i++) {
			UserAlert alert = alerts[i];
			if (!alert.isValid())
				continue;
			short level = alert.getPriorityClass();
			if (level < highestLevel)
				highestLevel = level;
			if (level <= UserAlert.CRITICAL_ERROR)
				numberOfCriticalError++;
			else if (level <= UserAlert.ERROR)
				numberOfError++;
			else if (level <= UserAlert.WARNING)
				numberOfWarning++;
			else if (level <= UserAlert.MINOR)
				numberOfMinor++;
			totalNumber++;
		}
		
		if (totalNumber == 0)
			return new HTMLNode("#", "");
		
		boolean separatorNeeded = false;
		int messageTypes=0;
		StringBuffer alertSummaryString = new StringBuffer(1024);
		if (numberOfCriticalError != 0) {
			alertSummaryString.append(l10n("criticalErrorCountLabel")).append(' ').append(numberOfCriticalError);
			separatorNeeded = true;
			messageTypes++;
		}
		if (numberOfError != 0) {
			if (separatorNeeded)
				alertSummaryString.append(" | ");
			alertSummaryString.append(l10n("errorCountLabel")).append(' ').append(numberOfError);
			separatorNeeded = true;
			messageTypes++;
		}
		if (numberOfWarning != 0) {
			if (separatorNeeded)
				alertSummaryString.append(" | ");
			alertSummaryString.append(l10n("warningCountLabel")).append(' ').append(numberOfWarning);
			separatorNeeded = true;
			messageTypes++;
		}
		if (numberOfMinor != 0) {
			if (separatorNeeded)
				alertSummaryString.append(" | ");
			alertSummaryString.append(l10n("minorCountLabel")).append(' ').append(numberOfMinor);
			separatorNeeded = true;
			messageTypes++;
		}
		if (messageTypes > 1) {
			alertSummaryString.append(" | ");
			alertSummaryString.append(l10n("totalLabel")).append(' ').append(totalNumber);
		}
		HTMLNode summaryBox = null;

		if (highestLevel <= UserAlert.CRITICAL_ERROR)
			summaryBox = new HTMLNode("div", "class", "infobox infobox-error");
		else if (highestLevel <= UserAlert.ERROR)
			summaryBox = new HTMLNode("div", "class", "infobox infobox-alert");
		else if (highestLevel <= UserAlert.WARNING)
			summaryBox = new HTMLNode("div", "class", "infobox infobox-warning");
		else if (highestLevel <= UserAlert.MINOR)
			summaryBox = new HTMLNode("div", "class", "infobox infobox-information");
		summaryBox.addChild("div", "class", "infobox-header", l10n("alertsTitle"));
		HTMLNode summaryContent = summaryBox.addChild("div", "class", "infobox-content", alertSummaryString.toString());
		L10n.addL10nSubstitution(summaryContent, "UserAlertManager.alertsOnHomepage",
				new String[] { "link", "/link" },
				new String[] { "<a href=\"/\">", "</a>" });
		return summaryBox;
	}

	private String l10n(String key) {
		return L10n.getString("UserAlertManager."+key);
	}

}
