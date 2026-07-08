package com.shif.shqip.android.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.TrackSelectionView;
import com.google.android.material.tabs.TabLayout;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings({"deprecation", "unused"})
public final class TrackSelectionDialog extends DialogFragment {

    public interface TrackSelectionListener {
        void onTracksSelected(TrackSelectionParameters trackSelectionParameters);
    }

    public static final ImmutableList<Integer> SUPPORTED_TRACK_TYPES = ImmutableList.of(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_TEXT);
    private final SparseArray<TrackSelectionViewFragment> tabFragments;
    private final ArrayList<Integer> tabTrackTypes;
    private int titleId;
    private DialogInterface.OnClickListener onClickListener;
    private DialogInterface.OnDismissListener onDismissListener;

    public static boolean willHaveContent(Player player) {
        return willHaveContent(player.getCurrentTracks());
    }

    public static boolean willHaveContent(Tracks tracks) {
        for (Tracks.Group trackGroup : tracks.getGroups()) {
            if (SUPPORTED_TRACK_TYPES.contains(trackGroup.getType())) {
                return true;
            }
        }
        return false;
    }

    public static TrackSelectionDialog createForPlayer(
            Player player, DialogInterface.OnDismissListener onDismissListener) {
        return createForTracksAndParameters(
                R.string.track_selection_title,
                player.getCurrentTracks(),
                player.getTrackSelectionParameters(),
                /* allowAdaptiveSelections= */ true,
                /* allowMultipleOverrides= */ false,
                player::setTrackSelectionParameters,
                onDismissListener);
    }

    /**
     * Creates a dialog for given {@link Tracks} and {@link TrackSelectionParameters}.
     *
     * @param titleId                  The resource id of the dialog title.
     * @param tracks                   The {@link Tracks} describing the tracks to display.
     * @param trackSelectionParameters The initial {@link TrackSelectionParameters}.
     * @param allowAdaptiveSelections  Whether adaptive selections (consisting of more than one track)
     * can be made.
     * @param allowMultipleOverrides   Whether tracks from multiple track groups can be selected.
     * @param trackSelectionListener   Called when tracks are selected.
     * @param onDismissListener        {@link DialogInterface.OnDismissListener} called when the dialog is
     * dismissed.
     */
    public static TrackSelectionDialog createForTracksAndParameters(
            int titleId,
            Tracks tracks,
            TrackSelectionParameters trackSelectionParameters,
            boolean allowAdaptiveSelections,
            boolean allowMultipleOverrides,
            TrackSelectionListener trackSelectionListener,
            DialogInterface.OnDismissListener onDismissListener) {
        TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
        trackSelectionDialog.init(
                tracks,
                trackSelectionParameters,
                titleId,
                allowAdaptiveSelections,
                allowMultipleOverrides,
                /* onClickListener= */ (dialog, which) -> {
                    TrackSelectionParameters.Builder builder = trackSelectionParameters.buildUpon();
                    for (int i = 0; i < SUPPORTED_TRACK_TYPES.size(); i++) {
                        int trackType = SUPPORTED_TRACK_TYPES.get(i);
                        builder.setTrackTypeDisabled(trackType, trackSelectionDialog.getIsDisabled(trackType));
                        builder.clearOverridesOfType(trackType);
                        Map<TrackGroup, TrackSelectionOverride> overrides = trackSelectionDialog.getOverrides(trackType);
                        for (TrackSelectionOverride override : overrides.values()) {
                            builder.addOverride(override);
                        }
                    }
                    trackSelectionListener.onTracksSelected(builder.build());
                },
                onDismissListener);
        return trackSelectionDialog;
    }

    public TrackSelectionDialog() {
        tabFragments = new SparseArray<>();
        tabTrackTypes = new ArrayList<>();
        // Retain instance across activity re-creation to prevent losing access to init data.
        setRetainInstance(true);
    }

    private void init(
            Tracks tracks,
            TrackSelectionParameters trackSelectionParameters,
            int titleId,
            boolean allowAdaptiveSelections,
            boolean allowMultipleOverrides,
            DialogInterface.OnClickListener onClickListener,
            DialogInterface.OnDismissListener onDismissListener) {
        this.titleId = titleId;
        this.onClickListener = onClickListener;
        this.onDismissListener = onDismissListener;

        for (int i = 0; i < SUPPORTED_TRACK_TYPES.size(); i++) {
            @C.TrackType int trackType = SUPPORTED_TRACK_TYPES.get(i);
            ArrayList<Tracks.Group> trackGroups = new ArrayList<>();
            for (Tracks.Group trackGroup : tracks.getGroups()) {
                if (trackGroup.getType() == trackType) {
                    trackGroups.add(trackGroup);
                }
            }
            if (!trackGroups.isEmpty()) {
                TrackSelectionViewFragment tabFragment = new TrackSelectionViewFragment();
                tabFragment.init(
                        trackGroups,
                        trackSelectionParameters.disabledTrackTypes.contains(trackType),
                        trackSelectionParameters.overrides,
                        allowAdaptiveSelections,
                        allowMultipleOverrides);
                tabFragments.put(trackType, tabFragment);
                tabTrackTypes.add(trackType);
            }
        }
    }

    /**
     * Returns whether the disabled option is selected for the specified track type.
     *
     * @param trackType The track type.
     * @return Whether the disabled option is selected for the track type.
     */
    public boolean getIsDisabled(int trackType) {
        TrackSelectionViewFragment trackView = tabFragments.get(trackType);
        return trackView != null && trackView.isDisabled;
    }

    /**
     * Returns the selected track overrides for the specified track type.
     *
     * @param trackType The track type.
     * @return The track overrides for the track type.
     */
    public Map<TrackGroup, TrackSelectionOverride> getOverrides(int trackType) {
        TrackSelectionViewFragment trackView = tabFragments.get(trackType);
        return trackView == null ? Collections.emptyMap() : trackView.overrides;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // We need to own the view to let tab layout work correctly on all API levels. We can't use
        // AlertDialog because it owns the view itself, so we use AppCompatDialog instead, themed using
        // the AlertDialog theme overlay with force-enabled title.
        AppCompatDialog dialog = new AppCompatDialog(requireActivity(), R.style.TrackSelectionDialogThemeOverlay);
        dialog.setCancelable(false);
        //dialog.setTitle(titleId);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return dialog;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        onDismissListener.onDismiss(dialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.dialog_track_selection, container, false);
        CardView lytDialog = dialogView.findViewById(R.id.lyt_dialog);
        SharedPref sharedPref = new SharedPref(requireActivity());
        TextView dialogTitle = dialogView.findViewById(R.id.dialog_title);
        dialogTitle.setText(titleId);
        TabLayout tabLayout = dialogView.findViewById(R.id.track_selection_dialog_tab_layout);
        ViewPager viewPager = dialogView.findViewById(R.id.track_selection_dialog_view_pager);
        Button cancelButton = dialogView.findViewById(R.id.track_selection_dialog_cancel_button);
        Button okButton = dialogView.findViewById(R.id.track_selection_dialog_ok_button);
        viewPager.setAdapter(new FragmentAdapter(getChildFragmentManager()));
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setVisibility(tabFragments.size() > 1 ? View.VISIBLE : View.GONE);
        cancelButton.setOnClickListener(view -> Tools.postDelayed(this::dismiss, 250));
        okButton.setOnClickListener(view -> Tools.postDelayed(()-> {
            onClickListener.onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
            dismiss();
        }, 250));

        if (sharedPref.getIsDarkTheme()) {
            lytDialog.setCardBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.color_dialog_background_dark));
            dialogTitle.setTextColor(ContextCompat.getColor(requireActivity(), R.color.color_white));

            // --- TAB LAYOUT COLOR MODIFICATION START ---
            int limeColor = ContextCompat.getColor(requireActivity(), R.color.color_dark_accent);
            int unselectedColor = Color.WHITE;

            // Set the indicator bar color to LIME
            tabLayout.setSelectedTabIndicatorColor(limeColor);

            // Define the states and corresponding colors
            int[][] states = new int[][] {
                    new int[]{android.R.attr.state_selected}, // selected
                    new int[]{} // default (unselected)
            };
            int[] colors = new int[] {
                    limeColor,   // Color when selected (LIME)
                    unselectedColor  // Color when unselected (White)
            };

            ColorStateList colorStateList = new ColorStateList(states, colors);
            tabLayout.setTabTextColors(colorStateList);
            // --- TAB LAYOUT COLOR MODIFICATION END ---

        } else {
            lytDialog.setCardBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.color_white));
        }

        return dialogView;
    }

    private static String getTrackTypeString(Resources resources, @C.TrackType int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_VIDEO:
                return resources.getString(com.google.android.exoplayer2.ui.R.string.exo_track_selection_title_video);
            case C.TRACK_TYPE_AUDIO:
                return resources.getString(com.google.android.exoplayer2.ui.R.string.exo_track_selection_title_audio);
            case C.TRACK_TYPE_TEXT:
                return resources.getString(com.google.android.exoplayer2.ui.R.string.exo_track_selection_title_text);
            default:
                throw new IllegalArgumentException();
        }
    }

    private final class FragmentAdapter extends FragmentPagerAdapter {

        public FragmentAdapter(FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return tabFragments.get(tabTrackTypes.get(position));
        }

        @Override
        public int getCount() {
            return tabTrackTypes.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getTrackTypeString(getResources(), tabTrackTypes.get(position));
        }
    }

    /**
     * Fragment to show a track selection in tab of the track selection dialog.
     */
    public static final class TrackSelectionViewFragment extends Fragment implements TrackSelectionView.TrackSelectionListener {

        private List<Tracks.Group> trackGroups;
        private boolean allowAdaptiveSelections;
        private boolean allowMultipleOverrides;

        /* package */ boolean isDisabled;
        /* package */ Map<TrackGroup, TrackSelectionOverride> overrides;

        public TrackSelectionViewFragment() {
            // Retain instance across activity re-creation to prevent losing access to init data.
            setRetainInstance(true);
        }

        public void init(List<Tracks.Group> trackGroups, boolean isDisabled, Map<TrackGroup, TrackSelectionOverride> overrides, boolean allowAdaptiveSelections, boolean allowMultipleOverrides) {
            this.trackGroups = trackGroups;
            this.isDisabled = isDisabled;
            this.allowAdaptiveSelections = allowAdaptiveSelections;
            this.allowMultipleOverrides = allowMultipleOverrides;
            // TrackSelectionView does this filtering internally, but we need to do it here as well to
            // handle the case where the TrackSelectionView is never created.
            this.overrides = new HashMap<>(TrackSelectionView.filterOverrides(overrides, trackGroups, allowMultipleOverrides));
        }

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

            SharedPref sharedPref = new SharedPref(requireActivity());
            LayoutInflater themedInflater = inflater;

            // --- CHECKBOX/RADIO BUTTON AND TRACK SELECTED ITEM TEXT COLOR MODIFICATION START ---
            if (sharedPref.getIsDarkTheme()) {
                // Apply a theme to force colorControlActivated (checkbox/text selected color) to LIME
                // R.style.TrackSelectionViewWhiteTheme must be defined in XML and set colorControlActivated to @color/color_lime
                Context context = new ContextThemeWrapper(requireContext(), R.style.TrackSelectionViewWhiteTheme);
                themedInflater = inflater.cloneInContext(context);
            }
            // --- CHECKBOX/RADIO BUTTON AND TRACK SELECTED ITEM TEXT COLOR MODIFICATION END ---

            View rootView = themedInflater.inflate(com.google.android.exoplayer2.ui.R.layout.exo_track_selection_dialog, container, /* attachToRoot= */ false);
            TrackSelectionView trackSelectionView = rootView.findViewById(R.id.exo_track_selection_view);
            trackSelectionView.setShowDisableOption(true);
            trackSelectionView.setAllowMultipleOverrides(allowMultipleOverrides);
            trackSelectionView.setAllowAdaptiveSelections(allowAdaptiveSelections);
            trackSelectionView.init(
                    trackGroups,
                    isDisabled,
                    overrides,
                    /* trackFormatComparator= */ null,
                    /* listener= */ this);

            // Removed the previous manual loop for setting all text color to white,
            // as the themedInflater now correctly handles the selected/unselected text color.

            return rootView;
        }

        // Removed the private helper method setChildTextColor as it is no longer needed.

        @Override
        public void onTrackSelectionChanged(boolean isDisabled, Map<TrackGroup, TrackSelectionOverride> overrides) {
            this.isDisabled = isDisabled;
            this.overrides = overrides;
        }
    }
}