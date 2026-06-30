package com.shif.shqip.android.utils;

import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.shif.shqip.android.R;
import com.shif.shqip.android.fragments.FragmentCategory;
import com.shif.shqip.android.fragments.FragmentFavorite;
import com.shif.shqip.android.fragments.FragmentRecent;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ViewPagerHelper {

    AppCompatActivity activity;
    MenuItem prevMenuItem;
    int pagerNumber = 3;

    public ViewPagerHelper(AppCompatActivity activity) {
        this.activity = activity;
    }

    public void setupViewPagerDefault(ViewPager viewPager, BottomNavigationView navigation, TextView titleToolbar) {
        viewPager.setVisibility(View.VISIBLE);
        viewPager.setAdapter(new AdapterNavigationDefault(activity.getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(pagerNumber);
        navigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_category) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (itemId == R.id.navigation_favorite) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (prevMenuItem != null) {
                    prevMenuItem.setChecked(false);
                } else {
                    navigation.getMenu().getItem(0).setChecked(false);
                }
                navigation.getMenu().getItem(position).setChecked(true);
                prevMenuItem = navigation.getMenu().getItem(position);

                if (viewPager.getCurrentItem() == 0) {
                    titleToolbar.setText(activity.getResources().getString(R.string.app_name));
                } else if (viewPager.getCurrentItem() == 1) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_category));
                } else if (viewPager.getCurrentItem() == 2) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_favorite));
                }

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    public void setupViewPagerCategory(ViewPager viewPager, BottomNavigationView navigation, TextView titleToolbar) {
        viewPager.setVisibility(View.VISIBLE);
        viewPager.setAdapter(new AdapterNavigationCategory(activity.getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(pagerNumber);
        navigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_category) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (itemId == R.id.navigation_favorite) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (prevMenuItem != null) {
                    prevMenuItem.setChecked(false);
                } else {
                    navigation.getMenu().getItem(0).setChecked(false);
                }
                navigation.getMenu().getItem(position).setChecked(true);
                prevMenuItem = navigation.getMenu().getItem(position);

                if (viewPager.getCurrentItem() == 0) {
                    titleToolbar.setText(activity.getResources().getString(R.string.app_name));
                } else if (viewPager.getCurrentItem() == 1) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_recent));
                } else if (viewPager.getCurrentItem() == 2) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_favorite));
                }

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    public void setupViewPagerRtlDefault(RtlViewPager viewPager, BottomNavigationView navigation, TextView titleToolbar) {
        viewPager.setVisibility(View.VISIBLE);
        viewPager.setAdapter(new AdapterNavigationDefault(activity.getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(pagerNumber);
        navigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_category) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (itemId == R.id.navigation_favorite) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (prevMenuItem != null) {
                    prevMenuItem.setChecked(false);
                } else {
                    navigation.getMenu().getItem(0).setChecked(false);
                }
                navigation.getMenu().getItem(position).setChecked(true);
                prevMenuItem = navigation.getMenu().getItem(position);

                if (viewPager.getCurrentItem() == 0) {
                    titleToolbar.setText(activity.getResources().getString(R.string.app_name));
                } else if (viewPager.getCurrentItem() == 1) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_category));
                } else if (viewPager.getCurrentItem() == 2) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_favorite));
                }

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    public void setupViewPagerRtlCategory(RtlViewPager viewPager, BottomNavigationView navigation, TextView titleToolbar) {
        viewPager.setVisibility(View.VISIBLE);
        viewPager.setAdapter(new AdapterNavigationCategory(activity.getSupportFragmentManager()));
        viewPager.setOffscreenPageLimit(pagerNumber);
        navigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_category) {
                viewPager.setCurrentItem(0);
                return true;
            } else if (itemId == R.id.navigation_home) {
                viewPager.setCurrentItem(1);
                return true;
            } else if (itemId == R.id.navigation_favorite) {
                viewPager.setCurrentItem(2);
                return true;
            }
            return false;
        });

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (prevMenuItem != null) {
                    prevMenuItem.setChecked(false);
                } else {
                    navigation.getMenu().getItem(0).setChecked(false);
                }
                navigation.getMenu().getItem(position).setChecked(true);
                prevMenuItem = navigation.getMenu().getItem(position);

                if (viewPager.getCurrentItem() == 0) {
                    titleToolbar.setText(activity.getResources().getString(R.string.app_name));
                } else if (viewPager.getCurrentItem() == 1) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_recent));
                } else if (viewPager.getCurrentItem() == 2) {
                    titleToolbar.setText(activity.getResources().getString(R.string.title_nav_favorite));
                }

            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public class AdapterNavigationDefault extends FragmentPagerAdapter {

        AdapterNavigationDefault(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new FragmentRecent();
                case 1:
                    return new FragmentCategory();
                case 2:
                    return new FragmentFavorite();
            }
            return null;
        }

        @Override
        public int getCount() {
            return pagerNumber;
        }

    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public class AdapterNavigationCategory extends FragmentPagerAdapter {

        AdapterNavigationCategory(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new FragmentCategory();
                case 1:
                    return new FragmentRecent();
                case 2:
                    return new FragmentFavorite();
            }
            return null;
        }

        @Override
        public int getCount() {
            return pagerNumber;
        }

    }
}
