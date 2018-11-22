package app.opass.ccip.fragment;

import android.app.Activity;
import android.graphics.PorterDuff.Mode;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.internal.bind.util.ISO8601Utils;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager.widget.ViewPager;
import app.opass.ccip.R;
import app.opass.ccip.adapter.ScheduleTabAdapter;
import app.opass.ccip.model.Submission;
import app.opass.ccip.network.ConfClient;
import app.opass.ccip.util.PreferenceUtil;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScheduleTabFragment extends Fragment {

    private static final SimpleDateFormat SDF_DATE = new SimpleDateFormat("MM/dd");
    SwipeRefreshLayout swipeRefreshLayout;
    TabLayout tabLayout;
    ViewPager viewPager;
    MenuItem menuItemStar;
    private Activity mActivity;
    private boolean starFilter = false;
    private List<Submission> mSubmissions;
    private ScheduleTabAdapter scheduleTabAdapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(R.layout.fragment_schedule_tab, container, false);

        swipeRefreshLayout = view.findViewById(R.id.swipeContainer);
        tabLayout = view.findViewById(R.id.tabs);
        viewPager = view.findViewById(R.id.pager);

        mActivity = getActivity();

        if (VERSION.SDK_INT >= 21) {
            mActivity.findViewById(R.id.appbar).setElevation(0);
        }

        setHasOptionsMenu(true);

        swipeRefreshLayout.setEnabled(false);
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
            }
        });

        Call<List<Submission>> submissionCall = ConfClient.get().submission();
        submissionCall.enqueue(new Callback<List<Submission>>() {
            @Override
            public void onResponse(Call<List<Submission>> call, Response<List<Submission>> response) {
                if (response.isSuccessful()) {
                    swipeRefreshLayout.setRefreshing(false);

                    mSubmissions = response.body();
                    PreferenceUtil.savePrograms(mActivity, mSubmissions);
                } else {
                    loadOfflineSchedule();
                }
                setupViewPager();
            }

            @Override
            public void onFailure(Call<List<Submission>> call, Throwable t) {
                loadOfflineSchedule();
                setupViewPager();
            }
        });

        return view;
    }

    @Override
    public void onDestroy() {
        tabLayout.setupWithViewPager(null);
        super.onDestroy();
    }

    private void setupViewPager() {
        if (isAdded()) {
            scheduleTabAdapter = new ScheduleTabAdapter(getChildFragmentManager());
            addSubmissionFragments(mSubmissions);
            viewPager.setAdapter(scheduleTabAdapter);
            tabLayout.setupWithViewPager(viewPager);
            menuItemStar.setVisible(true);
        }
    }

    private void addSubmissionFragments(List<Submission> submissions) {
        HashMap<String, List<Submission>> map = new HashMap<>();
        for (Submission submission : submissions) {
            try {
                String dateKey = SDF_DATE.format(ISO8601Utils.parse(submission.getStart(), new ParsePosition(0)));
                if (map.containsKey(dateKey)) {
                    List<Submission> tmp = map.get(dateKey);
                    tmp.add(submission);
                    map.put(dateKey, tmp);
                } else {
                    List<Submission> arrayList = new ArrayList<>();
                    arrayList.add(submission);
                    map.put(dateKey, arrayList);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        SortedSet<String> keys = new TreeSet<>(map.keySet());
        for (String key : keys) {
            List<Submission> value = map.get(key);
            scheduleTabAdapter.addFragment(ScheduleFragment.newInstance(key, value), key);
        }
        scheduleTabAdapter.notifyDataSetChanged();

        if (map.size() == 1) {
            tabLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.add("star")
                .setIcon(R.drawable.ic_bookmark_border_black_24dp)
                .setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        starFilter = !starFilter;
                        if (starFilter) {
                            item.setIcon(R.drawable.ic_bookmark_black_24dp);
                        } else {
                            item.setIcon(R.drawable.ic_bookmark_border_black_24dp);
                        }
                        item.getIcon().setColorFilter(getResources().getColor(R.color.colorWhite),
                                Mode.SRC_ATOP);
                        scheduleTabAdapter.toggleStarFilter(starFilter);
                        return false;
                    }
                })
                .setVisible(false)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menuItemStar = menu.getItem(0);
        menuItemStar.getIcon().setColorFilter(getResources().getColor(R.color.colorWhite),
                Mode.SRC_ATOP);
    }

    public void loadOfflineSchedule() {
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        Toast.makeText(mActivity, R.string.offline, Toast.LENGTH_LONG).show();
        List<Submission> submissions = PreferenceUtil.loadPrograms(mActivity);
        if (submissions != null) {
            mSubmissions = submissions;
        }
    }
}
