/*
 * Copyright (C) 2019 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.dryht.mobile.Adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.dryht.mobile.Activity.MainActivity;
import com.dryht.mobile.Activity.NewInfoActivity;
import com.dryht.mobile.R;
import com.dryht.mobile.utils.XToastUtils;
import com.xuexiang.xui.adapter.recyclerview.BaseRecyclerAdapter;
import com.xuexiang.xui.adapter.recyclerview.RecyclerViewHolder;
import com.xuexiang.xui.widget.banner.recycler.BannerLayout;
import com.xuexiang.xui.widget.imageview.ImageLoader;
import com.xuexiang.xui.widget.imageview.strategy.DiskCacheStrategyEnum;
import com.xuexiang.xui.widget.toast.XToast;


import org.json.JSONArray;
import org.json.JSONException;

import java.util.Arrays;
import java.util.List;


public class RecyclerViewBannerAdapter extends BaseRecyclerAdapter<String> {

    /**
     * 默认加载图片
     */
    private ColorDrawable mColorDrawable;
    private SharedPreferences sharedPreferences;
    /**
     * 是否允许进行缓存
     */
    private boolean mEnableCache = true;

    private BannerLayout.OnBannerItemClickListener mOnBannerItemClickListener;


    public RecyclerViewBannerAdapter() {
        super();
        mColorDrawable = new ColorDrawable(Color.parseColor("#555555"));
    }

    public RecyclerViewBannerAdapter(List<String> list) {
        super(list);
        mColorDrawable = new ColorDrawable(Color.parseColor("#555555"));
    }

    public RecyclerViewBannerAdapter(String[] list) {
        super(Arrays.asList(list));
        mColorDrawable = new ColorDrawable(Color.parseColor("#555555"));
    }

    /**
     * 适配的布局
     *
     * @param viewType
     * @return
     */
    @Override
    public int getItemLayoutId(int viewType) {
        return R.layout.adapter_recycler_view_banner_image_item;
    }

    /**
     * 绑定数据
     *
     * @param holder
     * @param position
     * @param imgUrl
     */
    @Override
    public void bindData(@NonNull RecyclerViewHolder holder, final int position, String imgUrl) {
        ImageView imageView = holder.findViewById(R.id.iv_item);
        if (!TextUtils.isEmpty(imgUrl)) {
            ImageLoader.get().loadImage(imageView, imgUrl, mColorDrawable,
                    mEnableCache ? DiskCacheStrategyEnum.RESOURCE : DiskCacheStrategyEnum.NONE);
        } else {
            imageView.setImageDrawable(mColorDrawable);
        }

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sharedPreferences= v.getContext().getSharedPreferences("data", Context.MODE_PRIVATE);
                Intent intent = new Intent(v.getContext(), NewInfoActivity.class);
                try {
                    intent.putExtra("newid",new JSONArray(sharedPreferences.getString("Top5News","")).getJSONObject(position).get("newid").toString());
                    v.getContext().startActivity(intent);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 设置是否允许缓存
     *
     * @param enableCache
     * @return
     */
    public RecyclerViewBannerAdapter enableCache(boolean enableCache) {
        mEnableCache = enableCache;
        return this;
    }

    /**
     * 获取是否允许缓存
     *
     * @return
     */
    public boolean getEnableCache() {
        return mEnableCache;
    }

    public ColorDrawable getColorDrawable() {
        return mColorDrawable;
    }

    public RecyclerViewBannerAdapter setColorDrawable(ColorDrawable colorDrawable) {
        mColorDrawable = colorDrawable;
        return this;
    }

    public RecyclerViewBannerAdapter setOnBannerItemClickListener(BannerLayout.OnBannerItemClickListener onBannerItemClickListener) {
        mOnBannerItemClickListener = onBannerItemClickListener;
        return this;
    }
}
