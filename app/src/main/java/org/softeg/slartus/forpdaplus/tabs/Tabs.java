package org.softeg.slartus.forpdaplus.tabs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.softeg.slartus.forpdacommon.NotReportException;
import org.softeg.slartus.forpdaplus.App;
import org.softeg.slartus.forpdaplus.R;

/**
 * User: slinkin
 * Date: 22.11.11
 * Time: 8:07
 */
public final class Tabs {

    public static final String TAB_QUICK_START = "QuickStart";

    public static final String TAB_DOWNLOADS = "DownloadsTab";


    public static final String[] templates = {
               TAB_DOWNLOADS};

    public static BaseTab create(Context context, String template, String tabId) {
        return create(context, template, tabId, null);
    }

    public static BaseTab create(Context context, String template, String tabId, ITabParent tabParent) {
        switch (template) {

            case QuickStartTab.TEMPLATE:
                return new QuickStartTab(context, tabId, tabParent, template);
            case DownloadsTab.TEMPLATE:
                return new DownloadsTab(context, tabParent);


        }



        return new QuickStartTab(context, tabId, tabParent,template);
    }

    public static String getTabName(SharedPreferences prefs, String tabId) throws NotReportException {
        String template = getTemplate(prefs, tabId);
        return getDefaultTemplateName(template);

    }

    public static String getDefaultTemplateName(String template) throws NotReportException {

        if (template.equals(DownloadsTab.TEMPLATE))
            return DownloadsTab.TITLE;


        throw new NotReportException(App.getInstance().getString(R.string.UnknownTemplate));
    }

    public static String getTemplate(SharedPreferences prefs, String tabId) {
        String template = prefs.getString(tabId + ".Template", "");
        if (!TextUtils.isEmpty(template)) return template;



        return TAB_QUICK_START;
    }

}
