package org.softeg.slartus.forpdaplus.listfragments;/*
 * Created by slinkin on 10.04.2014.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.softeg.slartus.forpdaapi.IListItem;
import org.softeg.slartus.forpdaplus.App;
import org.softeg.slartus.forpdaplus.Client;
import org.softeg.slartus.forpdaplus.R;
import org.softeg.slartus.forpdaplus.common.AppLog;
import org.softeg.slartus.forpdaplus.db.CacheDbHelper;
import org.softeg.slartus.forpdaplus.listfragments.adapters.ExpandableMyListAdapter;
import org.softeg.slartus.forpdaplus.prefs.Preferences;
import org.softeg.sqliteannotations.BaseDao;

import java.io.IOException;
import java.util.ArrayList;

public abstract class BaseExpandableListFragment extends BaseBrickFragment implements
        ExpandableListView.OnChildClickListener {
    protected ArrayList<ExpandableGroup> mData = new ArrayList<>();
    protected ArrayList<ExpandableGroup> mLoadResultList;
    protected ArrayList<ExpandableGroup> mCacheList = new ArrayList<>();

    private static final String TAG = "BaseExpandableListFragment";

    public static final String FIRST_VISIBLE_ROW_KEY = "FIRST_VISIBLE_ROW_KEY";
    public static final String TOP_KEY = "TOP_KEY";

    protected Handler mHandler = new Handler();
    private int m_FirstVisibleRow = 0;
    private int m_Top = 0;

    protected void createMenu() {

    }

    public BaseExpandableListFragment() {
super();
    }

    protected Bundle args;

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!Preferences.Lists.getScrollByButtons())
            return false;

        int action = event.getAction();

        ListView scrollView = getListView();
        int visibleItemsCount = scrollView.getLastVisiblePosition() - scrollView.getFirstVisiblePosition();

        int keyCode = event.getKeyCode();
        if(Preferences.System.isScrollUpButton(keyCode)){
            if (action == KeyEvent.ACTION_DOWN)
                scrollView.setSelection(Math.max(scrollView.getFirstVisiblePosition() - visibleItemsCount, 0));
            return true;// true надо обязательно возвращать даже если не ACTION_DOWN иначе звук нажатия
        }
        if(Preferences.System.isScrollDownButton(keyCode)){
            if (action == KeyEvent.ACTION_DOWN)
                scrollView.setSelection(Math.min(scrollView.getLastVisiblePosition(), scrollView.getCount() - 1));
            return true;// true надо обязательно возвращать даже если не ACTION_DOWN иначе звук нажатия
        }

        return false;
    }
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createMenu();
        if (getArguments() != null) {
            args = getArguments();
        }
        if (savedInstanceState != null) {
            args = savedInstanceState;
        }
        if (savedInstanceState != null) {

            m_FirstVisibleRow = savedInstanceState.getInt(FIRST_VISIBLE_ROW_KEY, m_FirstVisibleRow);
            m_Top = savedInstanceState.getInt(TOP_KEY, m_Top);
        }
    }

    private ExpandableListView mListView;
    private TextView mEmptyTextView;

    protected int getViewId() {
        return R.layout.expandable_list_fragment;
    }

    @Override
    public android.view.View onCreateView(android.view.LayoutInflater inflater, android.view.ViewGroup container, android.os.Bundle savedInstanceState) {
        View v = inflater.inflate(getViewId(), container, false);
        assert v != null;
        mListView = (ExpandableListView) v.findViewById(android.R.id.list);
        mListView.setOnChildClickListener(this);
        mEmptyTextView = (TextView) v.findViewById(android.R.id.empty);
        mListView.setEmptyView(mEmptyTextView);
        return v;
    }

    @Override
    public void onSaveInstanceState(android.os.Bundle outState) {
        if (args != null)
            outState.putAll(args);

        outState.putInt(FIRST_VISIBLE_ROW_KEY, m_FirstVisibleRow);
        outState.putInt(TOP_KEY, m_Top);
        super.onSaveInstanceState(outState);
    }

    protected BaseExpandableListAdapter mAdapter;


    public Context getContext() {
        return getActivity();
    }

    protected SwipeRefreshLayout mSwipeRefreshLayout;

    protected void saveListViewScrollPosition() {
        m_FirstVisibleRow = getListView().getFirstVisiblePosition();
        View v = getListView().getChildAt(0);
        m_Top = (v == null) ? 0 : v.getTop();
    }

    protected void restoreListViewScrollPosition() {
        getListView().setSelectionFromTop(m_FirstVisibleRow, m_Top);
    }

    protected ExpandableListView getListView() {
        return mListView;
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition,
                                long id) {
        return false;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSwipeRefreshLayout = createSwipeRefreshLayout(view);
    }

    protected SwipeRefreshLayout createSwipeRefreshLayout(View view) {
        // This is the View which is created by ListFragment
        ViewGroup viewGroup = (ViewGroup) view;

        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.ptr_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadData(true);
            }
        });
        swipeRefreshLayout.setColorSchemeResources(App.getInstance().getMainAccentColor());
        return swipeRefreshLayout;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        registerForContextMenu(getListView());
        setListShown(false);
        mAdapter = createAdapter();


        setListAdapter(mAdapter);

        mCacheTask = new LoadCacheTask();
        mCacheTask.execute();
    }

    private void setListAdapter(ExpandableListAdapter mAdapter) {
        getListView().setAdapter(mAdapter);
    }

    protected void setListShown(boolean b) {
        //mListView.setVisibility(b?);
    }

    public ExpandableListAdapter getAdapter() {
        return mAdapter;
    }



    protected BaseExpandableListAdapter createAdapter() {
        return new ExpandableMyListAdapter(getActivity(), mData);
    }

    protected void setLoading(final Boolean loading) {
        try {
            if (getActivity() == null) return;

            //mSwipeRefreshLayout.setRefreshing(loading);
            mSwipeRefreshLayout.post(new Runnable() {
                @Override
                public void run() {
                    mSwipeRefreshLayout.setRefreshing(loading);
                }
            });
            if (loading) {
                setEmptyText("Загрузка...");
            } else {
                setEmptyText("Нет данных");
            }
        } catch (Throwable ignore) {
            android.util.Log.e("TAG", ignore.toString());
        }
    }

    protected void setEmptyText(String s) {
        mEmptyTextView.setText(s);
    }

    public void filter(CharSequence text) {
        if (getAdapter() instanceof Filterable) {
            ((Filterable) getAdapter()).getFilter().filter(text);
        }
    }

    public void loadCache() throws IOException, IllegalAccessException, NoSuchFieldException, java.lang.InstantiationException {

    }

    public void saveCache() throws Exception {

    }

    protected <T> void updateItemCache(final T item, final Class<T> tClass, Boolean newThread) {
        if (newThread) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateItemCache(item, tClass);
                }
            }).start();
        } else {
            updateItemCache(item, tClass);
        }
    }

    private <T> void updateItemCache(T item, Class<T> tClass) {
        try {
            CacheDbHelper cacheDbHelper = new CacheDbHelper(App.getContext());
            SQLiteDatabase db = null;
            try {
                db = cacheDbHelper.getWritableDatabase();
                BaseDao<T> baseDao = new BaseDao<>(App.getContext(), db, getListName(), tClass);

                baseDao.update(item, ((IListItem) item).getId().toString());

            } finally {
                if (db != null)
                    db.close();
            }
        } catch (Throwable ex) {
            // Log.e(getContext(), ex);
        }
    }

    public void trySaveCache() {
        try {
            saveCache();
        } catch (Throwable e) {
            AppLog.eToast(getContext(), e);
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mCacheTask != null)
            mCacheTask.cancel(false);
        if (mTask != null)
            mTask.cancel(null);
    }

    private Task mTask = null;
    private LoadCacheTask mCacheTask = null;


    public void startLoad() {

        if (mTask != null)
            return;
        loadData(true);
    }

    protected Task createTask(Boolean isRefresh) {
        return new Task(isRefresh);
    }

    public void loadData(final boolean isRefresh) {
        saveListViewScrollPosition();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (needLogin()) {
                    Client.getInstance().checkLoginByCookies();
                    if (!Client.getInstance().getLogined())
                        Client.getInstance().showLoginForm(getContext(), new Client.OnUserChangedListener() {
                            public void onUserChanged(String user, Boolean success) {
                                loadData(isRefresh);
                            }
                        });
                }

                mTask = createTask(isRefresh);
                mTask.execute();
            }
        };
        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED)
            mTask.cancel(runnable);
        else {
            runnable.run();
        }
    }

    protected abstract boolean inBackground(boolean isRefresh) throws Throwable;

    protected abstract void deliveryResult(boolean isRefresh);

    private void beforeDeliveryResult() {
        saveListViewScrollPosition();
    }

    private void afterDeliveryResult() {

        setListShown(true);
        mAdapter.notifyDataSetChanged();
        setEmptyText("Нет данных");
        new Thread(new Runnable() {
            @Override
            public void run() {
                trySaveCache();
            }
        }).start();

        restoreListViewScrollPosition();
    }

    protected void onFailureResult() {

    }

    protected Boolean isCancelled() {
        return mTask.isCancelled();
    }

    public class Task extends AsyncTask<Boolean, Void, Boolean> {
        protected Boolean mRefresh;
        private Runnable onCancelAction;
        protected Throwable mEx;

        public Task(Boolean refresh) {
            mRefresh = refresh;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            setLoading(true);
        }

        public void cancel(Runnable runnable) {
            onCancelAction = runnable;

            cancel(false);
        }

        @Override
        protected Boolean doInBackground(Boolean[] p1) {
            try {

                return inBackground(mRefresh);
            } catch (Throwable e) {
                mEx = e;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (result && !isCancelled()) {
                beforeDeliveryResult();
                deliveryResult(mRefresh);
                afterDeliveryResult();
            }
            if (!isCancelled())
                setLoading(false);
            if (mEx != null)
                AppLog.e(getActivity(), mEx, new Runnable() {
                    @Override
                    public void run() {
                        loadData(mRefresh);
                    }
                });
            else if (!result) {
                onFailureResult();
            }
            new CheckTask().execute();
        }

        @Override
        protected void onCancelled(Boolean result) {
            if (onCancelAction != null)
                onCancelAction.run();
        }

        @Override
        protected void onCancelled() {
            if (onCancelAction != null)
                onCancelAction.run();
        }
    }

    public class CheckTask extends AsyncTask<Boolean, Void, Boolean> {
        private Throwable mEx;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected Boolean doInBackground(Boolean[] p1) {
            try {
                Client.getInstance().loadTestPage();
                return true;
            } catch (Throwable e) {
                mEx = e;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (mEx != null)
                Toast.makeText(getContext(), AppLog.getLocalizedMessage(mEx, "Ошибка проверки qms"), Toast.LENGTH_SHORT).show();
        }

    }

    public class LoadCacheTask extends AsyncTask<Boolean, Void, Boolean> {
        private Throwable mEx;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setLoading(true);
        }

        public void cancel() {
            cancel(false);
        }

        @Override
        protected Boolean doInBackground(Boolean[] p1) {
            try {
                loadCache();
                return true;
            } catch (Throwable e) {
                mEx = e;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (mEx != null)
                Toast.makeText(getContext(), AppLog.getLocalizedMessage(mEx, "Ошибка загрузки кешированного списка"), Toast.LENGTH_SHORT).show();
            if (!isCancelled()) {
                deliveryCache();
                restoreListViewScrollPosition();
                startLoad();
            }
        }

        @Override
        protected void onCancelled(Boolean result) {
            setLoading(false);
        }

        @Override
        protected void onCancelled() {
            setLoading(false);
        }

    }

    protected void deliveryCache() {
        mData.clear();
        if (mCacheList != null) {
            mData.addAll(mCacheList);
            mCacheList.clear();
        }

        mAdapter.notifyDataSetChanged();
    }

    public class ExpandableGroup {
        private String mName;
        private String mTitle;
        private ArrayList<IListItem> mData = new ArrayList<>();

        public ExpandableGroup(String name, String title) {
            mName = name;
            mTitle = title;
        }

        public ArrayList<IListItem> getChildren() {
            return mData;
        }

        public String getTitle() {
            return mTitle;
        }
    }
}
