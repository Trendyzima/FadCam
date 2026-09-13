package com.fadcam.production;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fadcam.R;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Production Room: lightweight planning/control center for professional video work.
 * Intentionally isolated from the recording lifecycle. */
public class ProductionActivity extends AppCompatActivity {
    private ProductionRepository repo;
    private LinearLayout content;
    private TextView empty;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repo = new ProductionRepository(this);
        setTitle("FadCam Production Room");
        buildUi();
        render();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        TextView title = text("Production Room", 26, true);
        root.addView(title);
        TextView subtitle = text("Plan productions, manage crews, scenes, schedules, assets and finances — offline first.", 14, false);
        root.addView(subtitle, lp(0, -2, 1));

        TextView add = text("＋  NEW PRODUCTION", 14, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(12), dp(12), dp(12), dp(12));
        add.setOnClickListener(v -> showCreateDialog());
        root.addView(add, lp(-1, 52, 0));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(12), 0, 0);
        scroll.addView(content);
        root.addView(scroll, lp(-1, 0, 1));
        setContentView(root);
    }

    private void render() {
        content.removeAllViews();
        List<ProductionRepository.Production> ps = repo.listProductions();
        if (ps.isEmpty()) {
            empty = text("No productions yet. Create your first project to open the full production workspace.", 15, false);
            empty.setPadding(dp(16), dp(28), dp(16), dp(28));
            content.addView(empty);
            return;
        }
        for (ProductionRepository.Production p : ps) addProductionCard(p);
    }

    private void addProductionCard(ProductionRepository.Production p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundColor(0xFF202124);
        LinearLayout.LayoutParams cp = lp(-1, -2, 0); cp.setMargins(0, 0, 0, dp(12));
        content.addView(card, cp);

        TextView name = text(p.name, 20, true); card.addView(name);
        card.addView(text(p.type + "  •  " + p.status + (p.director.isEmpty() ? "" : "  •  " + p.director), 13, false));
        card.addView(text("Budget " + money(p.budgetCents) + "   Spent " + money(p.expensesCents), 13, false));

        String metrics = "Episodes " + p.episodes + "   Scenes " + p.scenes + "   Tasks " + p.tasks
                + "\nCast/Crew " + p.castCrew + "   Locations " + p.locations + "   Gear " + p.equipment
                + "\nAssets " + p.assets + "   Call sheets " + p.callSheets + "   Reports " + p.reports + "   Scripts " + p.scripts;
        TextView m = text(metrics, 13, false); m.setPadding(0, dp(10), 0, dp(8)); card.addView(m);

        String modules = "Scenes & shots  •  Schedule  •  Call sheets  •  Cast & crew\n"
                + "Locations  •  Equipment  •  Scripts  •  Media/assets\n"
                + "Tasks/workflow  •  Daily reports  •  Contacts  •  Budget/expenses\n"
                + "Calendar  •  Notifications  •  Roles/permissions  •  Export/audit";
        TextView moduleText = text(modules, 12, false); card.addView(moduleText);

        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.END);
        TextView edit = text("EDIT", 12, true); edit.setPadding(dp(12), dp(12), dp(12), dp(12)); edit.setOnClickListener(v -> showEditDialog(p));
        TextView del = text("DELETE", 12, true); del.setPadding(dp(12), dp(12), dp(12), dp(12)); del.setOnClickListener(v -> confirmDelete(p));
        actions.addView(edit); actions.addView(del); card.addView(actions);
    }

    private void showCreateDialog() {
        LinearLayout box = form();
        EditText name = input("Production name");
        EditText director = input("Director / producer (optional)");
        Spinner type = spinner(new String[]{"Film", "TV Series", "Documentary", "Commercial", "Music Video", "Live Event", "Other"});
        box.addView(name); box.addView(director); box.addView(type);
        new AlertDialog.Builder(this).setTitle("Create production").setView(box)
                .setPositiveButton("Create", (d,w) -> { String n=name.getText().toString().trim(); if (!n.isEmpty()) { repo.createProduction(n, type.getSelectedItem().toString(), director.getText().toString().trim()); render(); } })
                .setNegativeButton("Cancel", null).show();
    }

    private void showEditDialog(ProductionRepository.Production p) {
        LinearLayout box = form();
        EditText name = input("Production name"); name.setText(p.name);
        EditText director = input("Director / producer"); director.setText(p.director);
        EditText budget = input("Budget (whole currency units)"); budget.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); budget.setText(String.valueOf(p.budgetCents / 100.0));
        EditText expenses = input("Expenses (whole currency units)"); expenses.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); expenses.setText(String.valueOf(p.expensesCents / 100.0));
        Spinner status = spinner(new String[]{"Planning", "Pre-production", "Production", "Post-production", "Completed", "Archived"});
        for (int i=0;i<status.getCount();i++) if (status.getItemAtPosition(i).toString().equals(p.status)) status.setSelection(i);
        box.addView(name); box.addView(director); box.addView(budget); box.addView(expenses); box.addView(status);
        new AlertDialog.Builder(this).setTitle("Production settings").setView(box)
                .setPositiveButton("Save", (d,w) -> { p.name=name.getText().toString().trim(); p.director=director.getText().toString().trim(); p.status=status.getSelectedItem().toString(); p.budgetCents=parseMoney(budget.getText().toString()); p.expensesCents=parseMoney(expenses.getText().toString()); repo.update(p); render(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmDelete(ProductionRepository.Production p) {
        new AlertDialog.Builder(this).setTitle("Delete production?").setMessage("This removes the local production workspace. Recorded media is not touched.")
                .setPositiveButton("Delete", (d,w)->{repo.delete(p.id); render();}).setNegativeButton("Cancel",null).show();
    }

    private LinearLayout form() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(8),0,dp(8),0); return l; }
    private EditText input(String hint) { EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setPadding(0,dp(8),0,dp(8)); return e; }
    private Spinner spinner(String[] values) { Spinner s=new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return s; }
    private TextView text(String s,int size,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xFFFFFFFF);if(bold)t.setTypeface(t.getTypeface(),1);return t;}
    private LinearLayout.LayoutParams lp(int w,int h,float weight){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h),weight);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private String money(long cents){return NumberFormat.getCurrencyInstance(Locale.getDefault()).format(cents/100.0);}
    private long parseMoney(String s){try{return Math.round(Double.parseDouble(s.replace(",",""))*100);}catch(Exception e){return 0;}}
}
