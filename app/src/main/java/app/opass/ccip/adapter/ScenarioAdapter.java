package app.opass.ccip.adapter;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import app.opass.ccip.R;
import app.opass.ccip.activity.CountdownActivity;
import app.opass.ccip.model.Attendee;
import app.opass.ccip.model.Error;
import app.opass.ccip.model.Scenario;
import app.opass.ccip.network.CCIPClient;
import app.opass.ccip.network.ErrorUtil;
import app.opass.ccip.util.JsonUtil;
import app.opass.ccip.util.LocaleUtil;
import app.opass.ccip.util.PreferenceUtil;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ScenarioAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("MM/dd HH:mm");
    private static final String FORMAT_TIMERANGE = "%s ~ %s";

    private Context mContext;
    private List<Scenario> mScenarioList;

    public ScenarioAdapter(Context context, List<Scenario> scenarioList) {
        mContext = context;
        mScenarioList = scenarioList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scenario, parent, false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        final ViewHolder holder = ((ViewHolder) viewHolder);

        final Scenario scenario = mScenarioList.get(position);

        try {
            int iconResId = mContext.getResources().getIdentifier(scenario.getId().indexOf("lunch") > 0 ? "lunch" : scenario.getId(), "drawable", mContext.getPackageName());
            holder.scenarioIcon.setImageDrawable(ContextCompat.getDrawable(mContext, iconResId));
        } catch (Resources.NotFoundException e) {
            holder.scenarioIcon.setImageResource(R.drawable.doc);
        }
        holder.scenarioIcon.setAlpha(1f);

        if (LocaleUtil.getCurrentLocale(mContext).toString().startsWith(Locale.TAIWAN.toString())) {
            holder.scenarioName.setText(scenario.getDisplayText().getZhTW());
        } else {
            holder.scenarioName.setText(scenario.getDisplayText().getEnUS());
        }

        holder.scenarioName.setTextColor(mContext.getResources().getColor(android.R.color.black));
        holder.allowTimeRange.setText(String.format(FORMAT_TIMERANGE,
                SDF.format(new Date(scenario.getAvailableTime() * 1000L)),
                SDF.format(new Date(scenario.getExpireTime() * 1000L))));

        if (scenario.getDisabled() != null) {
            setCardDisabled(holder, scenario.getDisabled());
            return;
        }

        if (scenario.getUsed() == null) {
            holder.card.setClickable(true);
            holder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (scenario.getCountdown() > 0) {
                        showConfirmDialog(scenario);
                    } else {
                        use(scenario);
                    }
                }
            });
        } else {
            if (scenario.getCountdown() > 0) {
                holder.card.setClickable(true);
                holder.card.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        startCountdownActivity(scenario);
                    }
                });
            } else {
                holder.card.setClickable(false);
                holder.card.setOnClickListener(null);
            }

            if (new Date().getTime() / 1000 > scenario.getUsed() + scenario.getCountdown()) {
                setCardUsed(holder);
            }
        }
    }

    public void showConfirmDialog(final Scenario scenario) {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.confirm_dialog_title)
                .setPositiveButton(R.string.positive_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        use(scenario);
                    }
                })
                .setNegativeButton(R.string.negative_button, null)
                .show();
    }

    public void startCountdownActivity(Scenario scenario) {
        Intent intent = new Intent();
        intent.setClass(mContext, CountdownActivity.class);
        intent.putExtra(CountdownActivity.INTENT_EXTRA_SCENARIO, JsonUtil.toJson(scenario));
        mContext.startActivity(intent);
    }

    public void use(final Scenario scenario) {
        Call<Attendee> attendeeCall = CCIPClient.get().use(scenario.getId(), PreferenceUtil.getToken(mContext));
        attendeeCall.enqueue(new Callback<Attendee>() {
            @Override
            public void onResponse(Call<Attendee> call, Response<Attendee> response) {
                if (response.isSuccessful()) {
                    Attendee attendee = response.body();
                    mScenarioList = attendee.getScenarios();
                    notifyDataSetChanged();

                    if (scenario.getCountdown() > 0) {
                        startCountdownActivity(scenario);
                    }
                } else {
                    if (response.code() == 400) {
                        Error error = ErrorUtil.parseError(response);
                        Toast.makeText(mContext, error.getMessage(), Toast.LENGTH_LONG).show();
                    } else if (response.code() == 403) {
                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.connect_to_conference_wifi)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    } else {
                        Toast.makeText(mContext, "Unexpected response", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<Attendee> call, Throwable t) {
                Toast.makeText(mContext, "Use req fail, " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setCardUsed(ViewHolder holder) {
        holder.tickIcon.setVisibility(View.VISIBLE);
        holder.scenarioIcon.setAlpha(0.4f);
        holder.scenarioName.setTextColor(Color.parseColor("#FF9B9B9B"));
    }

    private void setCardDisabled(ViewHolder holder, String reason) {
        holder.allowTimeRange.setText(reason);
        holder.card.setClickable(false);
        holder.card.setOnClickListener(null);
        holder.scenarioIcon.setAlpha(0.4f);
        holder.scenarioName.setTextColor(Color.parseColor("#FF9B9B9B"));
    }

    @Override
    public int getItemCount() {
        return mScenarioList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ImageView scenarioIcon, tickIcon;
        public TextView scenarioName, allowTimeRange;
        public CardView card;

        public ViewHolder(View itemView) {
            super(itemView);
            scenarioIcon = itemView.findViewById(R.id.icon);
            tickIcon = itemView.findViewById(R.id.tick);
            scenarioName = itemView.findViewById(R.id.scenario_name);
            allowTimeRange = itemView.findViewById(R.id.allow_time_range);
            card = itemView.findViewById(R.id.card);
        }
    }
}
