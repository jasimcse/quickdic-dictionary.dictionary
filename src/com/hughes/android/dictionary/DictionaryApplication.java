// Copyright 2011 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.hughes.android.dictionary;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.MenuItem.OnMenuItemClickListener;
import com.hughes.android.dictionary.DictionaryInfo.IndexInfo;
import com.hughes.android.dictionary.engine.Dictionary;
import com.hughes.android.dictionary.engine.Language;
import com.hughes.android.dictionary.engine.Language.LanguageResources;
import com.hughes.android.dictionary.engine.TransliteratorManager;
import com.hughes.android.util.PersistentObjectCache;
import com.hughes.util.ListUtil;
import com.ibm.icu.text.Collator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DictionaryApplication extends Application {

    static final String LOG = "QuickDicApp";

    // Static, determined by resources (and locale).
    // Unordered.
    static Map<String, DictionaryInfo> DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO = null;

    static final class DictionaryConfig implements Serializable {
        private static final long serialVersionUID = -1444177164708201263L;
        // User-ordered list, persisted, just the ones that are/have been
        // present.
        final List<String> dictionaryFilesOrdered = new ArrayList<String>();

        final Map<String, DictionaryInfo> uncompressedFilenameToDictionaryInfo = new LinkedHashMap<String, DictionaryInfo>();
        
        /**
         * Sometimes a deserialized version of this data structure isn't valid.
         * @return
         */
        boolean isValid() {
            return uncompressedFilenameToDictionaryInfo != null && dictionaryFilesOrdered != null;
        }
    }

    DictionaryConfig dictionaryConfig = null;

    int languageButtonPixels = -1;

    static synchronized void staticInit(final Context context) {
        if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO != null) {
            return;
        }
        DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO = new LinkedHashMap<String, DictionaryInfo>();
        final BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getResources().openRawResource(R.raw.dictionary_info)));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.length() == 0) {
                    continue;
                }
                final DictionaryInfo dictionaryInfo = new DictionaryInfo(line);
                DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO.put(
                        dictionaryInfo.uncompressedFilename, dictionaryInfo);
            }
            reader.close();
        } catch (IOException e) {
            Log.e(LOG, "Failed to load downloadable dictionary lists.", e);
        }
    }

    private File dictDir;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("QuickDic", "Application: onCreate");
        TransliteratorManager.init(null);
        staticInit(getApplicationContext());

        languageButtonPixels = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());

        // Load the dictionaries we know about.
        dictionaryConfig = PersistentObjectCache.init(getApplicationContext()).read(
                C.DICTIONARY_CONFIGS, DictionaryConfig.class);
        if (dictionaryConfig == null) {
            dictionaryConfig = new DictionaryConfig();
        }
        if (!dictionaryConfig.isValid()) {
            dictionaryConfig = new DictionaryConfig();
        }

        // Theme stuff.
        setTheme(getSelectedTheme().themeId);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                    String key) {
                Log.d("QuickDic", "prefs changed: " + key);
                if (key.equals(getString(R.string.themeKey))) {
                    setTheme(getSelectedTheme().themeId);
                }
            }
        });
    }

    public void onCreateGlobalOptionsMenu(
            final Context context, final Menu menu) {
        final MenuItem about = menu.add(getString(R.string.about));
        about.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        about.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                final Intent intent = new Intent().setClassName(AboutActivity.class
                        .getPackage().getName(), AboutActivity.class.getCanonicalName());
                context.startActivity(intent);
                return false;
            }
        });

        final MenuItem help = menu.add(getString(R.string.help));
        help.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        help.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                context.startActivity(HtmlDisplayActivity.getHelpLaunchIntent());
                return false;
            }
        });

        final MenuItem preferences = menu.add(getString(R.string.settings));
        preferences.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        preferences.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                PreferenceActivity.prefsMightHaveChanged = true;
                final Intent intent = new Intent().setClassName(PreferenceActivity.class
                        .getPackage().getName(), PreferenceActivity.class.getCanonicalName());
                context.startActivity(intent);
                return false;
            }
        });

        final MenuItem reportIssue = menu.add(getString(R.string.reportIssue));
        reportIssue.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        reportIssue.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            public boolean onMenuItemClick(final MenuItem menuItem) {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri
                        .parse("http://code.google.com/p/quickdic-dictionary/issues/entry"));
                context.startActivity(intent);
                return false;
            }
        });
    }

    public synchronized File getDictDir() {
        // This metaphor doesn't work, because we've already reset
        // prefsMightHaveChanged.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String dir = prefs.getString(getString(R.string.quickdicDirectoryKey), "");
        if (dir.isEmpty()) {
            final File defaultDictDir = new File(Environment.getExternalStorageDirectory(), "quickDic");
            dir = defaultDictDir.getAbsolutePath();
        }
        dictDir = new File(dir);
        dictDir.mkdirs();
        return dictDir;
    }

    public File getWordListFile() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String file = prefs.getString(getString(R.string.wordListFileKey), "");
        if (file.isEmpty()) {
            return new File(getDictDir(), "wordList.txt");
        }
        return new File(file);
    }

    public C.Theme getSelectedTheme() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String theme = prefs.getString(getString(R.string.themeKey), "themeLight");
        if (theme.equals("themeLight")) {
            return C.Theme.LIGHT;
        } else {
            return C.Theme.DEFAULT;
        }
    }

    public File getPath(String uncompressedFilename) {
        return new File(getDictDir(), uncompressedFilename);
    }

    String defaultLangISO2 = Locale.getDefault().getLanguage().toLowerCase();
    String defaultLangName = null;
    final Map<String, String> fileToNameCache = new LinkedHashMap<String, String>();

    public String isoCodeToLocalizedLanguageName(final String isoCode) {
        final Language.LanguageResources languageResources = Language.isoCodeToResources
                .get(isoCode);
        final String lang = languageResources != null ? getApplicationContext().getString(
                languageResources.nameId) : isoCode;
        return lang;
    }

    public List<IndexInfo> sortedIndexInfos(List<IndexInfo> indexInfos) {
        // Hack to put the default locale first in the name.
        if (indexInfos.size() > 1 &&
                indexInfos.get(1).shortName.toLowerCase().equals(defaultLangISO2)) {
            List<IndexInfo> result = new ArrayList<DictionaryInfo.IndexInfo>(indexInfos);
            ListUtil.swap(result, 0, 1);
            return result;
        }
        return indexInfos;
    }

    public synchronized String getDictionaryName(final String uncompressedFilename) {
        final String currentLocale = Locale.getDefault().getLanguage().toLowerCase();
        if (!currentLocale.equals(defaultLangISO2)) {
            defaultLangISO2 = currentLocale;
            fileToNameCache.clear();
            defaultLangName = null;
        }
        if (defaultLangName == null) {
            defaultLangName = isoCodeToLocalizedLanguageName(defaultLangISO2);
        }

        String name = fileToNameCache.get(uncompressedFilename);
        if (name != null) {
            return name;
        }

        final DictionaryInfo dictionaryInfo = DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                .get(uncompressedFilename);
        if (dictionaryInfo != null) {
            final StringBuilder nameBuilder = new StringBuilder();

            List<IndexInfo> sortedIndexInfos = sortedIndexInfos(dictionaryInfo.indexInfos);
            for (int i = 0; i < sortedIndexInfos.size(); ++i) {
                if (i > 0) {
                    nameBuilder.append("-");
                }
                nameBuilder
                        .append(isoCodeToLocalizedLanguageName(sortedIndexInfos.get(i).shortName));
            }
            name = nameBuilder.toString();
        } else {
            name = uncompressedFilename.replace(".quickdic", "");
        }
        fileToNameCache.put(uncompressedFilename, name);
        return name;
    }

    public View createButton(final Context context, final DictionaryInfo dictionaryInfo,
            final IndexInfo indexInfo) {
        LanguageResources languageResources = Language.isoCodeToResources.get(indexInfo.shortName);
        View result;

        if (languageResources == null || languageResources.flagId <= 0) {
            Button button = new Button(context);
            button.setText(indexInfo.shortName);
            result = button;
        } else {
            ImageButton button = new ImageButton(context);
            button.setImageResource(languageResources.flagId);
            button.setScaleType(ScaleType.FIT_CENTER);
            result = button;
        }
        result.setMinimumWidth(languageButtonPixels);
        result.setMinimumHeight(languageButtonPixels * 2 / 3);
        // result.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
        // LayoutParams.WRAP_CONTENT));
        return result;
    }

    public synchronized void moveDictionaryToTop(final DictionaryInfo dictionaryInfo) {
        dictionaryConfig.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename);
        dictionaryConfig.dictionaryFilesOrdered.add(0, dictionaryInfo.uncompressedFilename);
        PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
    }

    public synchronized void deleteDictionary(final DictionaryInfo dictionaryInfo) {
        while (dictionaryConfig.dictionaryFilesOrdered.remove(dictionaryInfo.uncompressedFilename)) {
        }
        ;
        dictionaryConfig.uncompressedFilenameToDictionaryInfo
                .remove(dictionaryInfo.uncompressedFilename);
        getPath(dictionaryInfo.uncompressedFilename).delete();
        PersistentObjectCache.getInstance().write(C.DICTIONARY_CONFIGS, dictionaryConfig);
    }

    final Collator collator = Collator.getInstance();
    final Comparator<String> uncompressedFilenameComparator = new Comparator<String>() {
        @Override
        public int compare(String uncompressedFilename1, String uncompressedFilename2) {
            final String name1 = getDictionaryName(uncompressedFilename1);
            final String name2 = getDictionaryName(uncompressedFilename2);
            if (defaultLangName.length() > 0) {
                if (name1.startsWith(defaultLangName + "-")
                        && !name2.startsWith(defaultLangName + "-")) {
                    return -1;
                } else if (name2.startsWith(defaultLangName + "-")
                        && !name1.startsWith(defaultLangName + "-")) {
                    return 1;
                }
            }
            return collator.compare(name1, name2);
        }
    };
    final Comparator<DictionaryInfo> dictionaryInfoComparator = new Comparator<DictionaryInfo>() {
        @Override
        public int compare(DictionaryInfo d1, DictionaryInfo d2) {
            // Single-index dictionaries first.
            if (d1.indexInfos.size() != d2.indexInfos.size()) {
                return d1.indexInfos.size() - d2.indexInfos.size();
            }
            return uncompressedFilenameComparator.compare(d1.uncompressedFilename,
                    d2.uncompressedFilename);
        }
    };

    public void backgroundUpdateDictionaries(final Runnable onUpdateFinished) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final DictionaryConfig oldDictionaryConfig = new DictionaryConfig();
                synchronized (this) {
                    oldDictionaryConfig.dictionaryFilesOrdered
                            .addAll(dictionaryConfig.dictionaryFilesOrdered);
                }
                final DictionaryConfig newDictionaryConfig = new DictionaryConfig();
                for (final String uncompressedFilename : oldDictionaryConfig.dictionaryFilesOrdered) {
                    final File dictFile = getPath(uncompressedFilename);
                    final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(dictFile);
                    if (dictionaryInfo != null) {
                        newDictionaryConfig.dictionaryFilesOrdered.add(uncompressedFilename);
                        newDictionaryConfig.uncompressedFilenameToDictionaryInfo.put(
                                uncompressedFilename, dictionaryInfo);
                    }
                }

                // Are there dictionaries on the device that we didn't know
                // about already?
                // Pick them up and put them at the end of the list.
                final List<String> toAddSorted = new ArrayList<String>();
                final File[] dictDirFiles = getDictDir().listFiles();
                if (dictDirFiles != null) {
                    for (final File file : dictDirFiles) {
                        if (file.getName().endsWith(".zip")) {
                            if (DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                                    .containsKey(file.getName().replace(".zip", ""))) {
                                file.delete();
                            }
                        }
                        if (!file.getName().endsWith(".quickdic")) {
                            continue;
                        }
                        if (newDictionaryConfig.uncompressedFilenameToDictionaryInfo
                                .containsKey(file.getName())) {
                            // We have it in our list already.
                            continue;
                        }
                        final DictionaryInfo dictionaryInfo = Dictionary.getDictionaryInfo(file);
                        if (dictionaryInfo == null) {
                            Log.e(LOG, "Unable to parse dictionary: " + file.getPath());
                            continue;
                        }

                        toAddSorted.add(file.getName());
                        newDictionaryConfig.uncompressedFilenameToDictionaryInfo.put(
                                file.getName(), dictionaryInfo);
                    }
                } else {
                    Log.w(LOG, "dictDir is not a diretory: " + getDictDir().getPath());
                }
                if (!toAddSorted.isEmpty()) {
                    Collections.sort(toAddSorted, uncompressedFilenameComparator);
                    newDictionaryConfig.dictionaryFilesOrdered.addAll(toAddSorted);
                }

                PersistentObjectCache.getInstance()
                        .write(C.DICTIONARY_CONFIGS, newDictionaryConfig);
                synchronized (this) {
                    dictionaryConfig = newDictionaryConfig;
                }

                try {
                    onUpdateFinished.run();
                } catch (Exception e) {
                    Log.e(LOG, "Exception running callback.", e);
                }
            }
        }).start();
    }

    public boolean matchesFilters(final DictionaryInfo dictionaryInfo, final String[] filters) {
        if (filters == null) {
            return true;
        }
        for (final String filter : filters) {
            if (!getDictionaryName(dictionaryInfo.uncompressedFilename).toLowerCase().contains(
                    filter)) {
                return false;
            }
        }
        return true;
    }

    public synchronized List<DictionaryInfo> getDictionariesOnDevice(String[] filters) {
        final List<DictionaryInfo> result = new ArrayList<DictionaryInfo>(
                dictionaryConfig.dictionaryFilesOrdered.size());
        for (final String uncompressedFilename : dictionaryConfig.dictionaryFilesOrdered) {
            final DictionaryInfo dictionaryInfo = dictionaryConfig.uncompressedFilenameToDictionaryInfo
                    .get(uncompressedFilename);
            if (dictionaryInfo != null && matchesFilters(dictionaryInfo, filters)) {
                result.add(dictionaryInfo);
            }
        }
        return result;
    }

    public List<DictionaryInfo> getDownloadableDictionaries(String[] filters) {
        final List<DictionaryInfo> result = new ArrayList<DictionaryInfo>(
                dictionaryConfig.dictionaryFilesOrdered.size());

        final Map<String, DictionaryInfo> remaining = new LinkedHashMap<String, DictionaryInfo>(
                DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO);
        remaining.keySet().removeAll(dictionaryConfig.dictionaryFilesOrdered);
        for (final DictionaryInfo dictionaryInfo : remaining.values()) {
            if (matchesFilters(dictionaryInfo, filters)) {
                result.add(dictionaryInfo);
            }
        }
        Collections.sort(result, dictionaryInfoComparator);
        return result;
    }

    public synchronized boolean isDictionaryOnDevice(String uncompressedFilename) {
        return dictionaryConfig.uncompressedFilenameToDictionaryInfo.get(uncompressedFilename) != null;
    }

    public boolean updateAvailable(final DictionaryInfo dictionaryInfo) {
        final DictionaryInfo downloadable =
                DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO.get(
                        dictionaryInfo.uncompressedFilename);
        return downloadable != null &&
                downloadable.creationMillis > dictionaryInfo.creationMillis;
    }

    public DictionaryInfo getDownloadable(final String uncompressedFilename) {
        final DictionaryInfo downloadable = DOWNLOADABLE_UNCOMPRESSED_FILENAME_NAME_TO_DICTIONARY_INFO
                .get(uncompressedFilename);
        return downloadable;
    }

}
