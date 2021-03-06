package com.lithium.leona.openstud.fragments;

import android.app.Activity;

import androidx.fragment.app.Fragment;

import com.lithium.leona.openstud.data.InfoManager;
import com.lithium.leona.openstud.helpers.ClientHelper;

import lithium.openstud.driver.core.Openstud;

public abstract class BaseDataFragment extends Fragment {
    Openstud os;

    public BaseDataFragment() {
        super();
    }

    public boolean initData() {
        Activity activity = getActivity();
        if (activity == null) return false;
        os = InfoManager.getOpenStud(activity);
        if (os == null) {
            ClientHelper.rebirthApp(activity, null);
            return false;
        }
        return true;
    }
}
