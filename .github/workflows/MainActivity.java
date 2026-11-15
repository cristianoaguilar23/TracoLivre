package br.com.cristianoaguilar.tracolivre;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private DrawingView drawing;
    private TextView toolLabel;
    private int selectedColor = Color.BLACK;
    private static final int REQ_OPEN = 41, REQ_SAVE = 42;
    private final int[] palette = {
        Color.BLACK, Color.WHITE, 0xFFE53935, 0xFFFF9800, 0xFFFFEB3B,
        0xFF4CAF50, 0xFF00BCD4, 0xFF2196F3, 0xFF673AB7, 0xFFE91E63,
        0xFF795548, 0xFF9E9E9E
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showWelcome();
        buildInterface();
        restoreAutosave();
    }

    private void showWelcome() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(36, 30, 36, 30);
        box.setBackgroundColor(Color.WHITE);
        TextView logo = new TextView(this);
        logo.setText("✦");
        logo.setTextSize(52);
        logo.setTextColor(0xFF5C6BC0);
        logo.setGravity(Gravity.CENTER);
        TextView title = new TextView(this);
        title.setText("TRAÇO LIVRE");
        title.setTextSize(25);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF20243A);
        title.setGravity(Gravity.CENTER);
        TextView subtitle = new TextView(this);
        subtitle.setText("Seu espaço para criar");
        subtitle.setTextSize(15);
        subtitle.setTextColor(0xFF68708A);
        subtitle.setGravity(Gravity.CENTER);
        TextView dev = new TextView(this);
        dev.setText("Desenvolvido por\nCristiano Aguilar");
        dev.setTextSize(13);
        dev.setTextColor(0xFF68708A);
        dev.setGravity(Gravity.CENTER);
        dev.setPadding(0, 24, 0, 0);
        box.addView(logo); box.addView(title); box.addView(subtitle); box.addView(dev);
        new AlertDialog.Builder(this).setView(box).setPositiveButton("COMEÇAR", (d,w)->{}).show();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF2F4FA);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(14, 8, 14, 8);
        header.setBackgroundColor(Color.WHITE);
        TextView brand = new TextView(this);
        brand.setText("✦  Traço Livre");
        brand.setTextColor(0xFF252A44);
        brand.setTextSize(20);
        brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, 52, 1));
        Button gallery = button("Galeria");
        gallery.setOnClickListener(v -> showGallery());
        header.addView(gallery);
        Button open = button("Abrir foto");
        open.setOnClickListener(v -> openImage());
        header.addView(open);
        root.addView(header);

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(8, 7, 8, 7);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setBackgroundColor(0xFFE8EAF5);
        addTool(tools, "Lápis", DrawingView.TOOL_PEN);
        addTool(tools, "Borracha", DrawingView.TOOL_ERASER);
        addTool(tools, "Linha", DrawingView.TOOL_LINE);
        addTool(tools, "Retângulo", DrawingView.TOOL_RECT);
        addTool(tools, "Círculo", DrawingView.TOOL_CIRCLE);
        addTool(tools, "Texto", DrawingView.TOOL_TEXT);
        addTool(tools, "Balde", DrawingView.TOOL_FILL);
        Button brushes = button("Pincéis");
        brushes.setOnClickListener(v -> showBrushPicker());
        tools.addView(brushes);
        toolsScroll.addView(tools);
        root.addView(toolsScroll);

        LinearLayout sizePanel = new LinearLayout(this);
        sizePanel.setGravity(Gravity.CENTER_VERTICAL);
        sizePanel.setPadding(14, 2, 14, 2);
        sizePanel.setBackgroundColor(Color.WHITE);
        TextView sizeTitle = new TextView(this);
        sizeTitle.setText("Espessura");
        sizeTitle.setTextColor(0xFF30354F);
        sizePanel.addView(sizeTitle);
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(40);
        sizeBar.setProgress(7);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (drawing != null) drawing.setStrokeWidth(Math.max(1, progress + 1));
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sizePanel.addView(sizeBar, new LinearLayout.LayoutParams(0, 42, 1));
        root.addView(sizePanel);

        LinearLayout layerBar = new LinearLayout(this);
        layerBar.setGravity(Gravity.CENTER_VERTICAL);
        layerBar.setPadding(8, 3, 8, 3);
        layerBar.setBackgroundColor(0xFFF7F8FC);
        TextView layerInfo = new TextView(this);
        layerInfo.setText("Camada 1");
        layerInfo.setTextColor(0xFF30354F);
        layerInfo.setTypeface(null, Typeface.BOLD);
        layerBar.addView(layerInfo, new LinearLayout.LayoutParams(0, 42, 1));
        Button addLayer = button("+ Camada");
        addLayer.setOnClickListener(v -> { drawing.addLayer(); updateLayerInfo(layerInfo); });
        layerBar.addView(addLayer);
        Button manageLayers = button("Camadas");
        manageLayers.setOnClickListener(v -> showLayerManager(layerInfo));
        layerBar.addView(manageLayers);
        Button visibility = button("Olho");
        visibility.setOnClickListener(v -> {
            drawing.toggleActiveLayerVisibility();
            updateLayerInfo(layerInfo);
        });
        layerBar.addView(visibility);
        Button removeLayer = button("Excluir");
        removeLayer.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setMessage("Excluir ou limpar a camada atual?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar", (d,w) -> { drawing.deleteActiveLayer(); updateLayerInfo(layerInfo); }).show());
        layerBar.addView(removeLayer);
        root.addView(layerBar);

        drawing = new DrawingView(this);
        drawing.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasParams.setMargins(10, 10, 10, 8);
        root.addView(drawing, canvasParams);

        LinearLayout paletteRow = new LinearLayout(this);
        paletteRow.setGravity(Gravity.CENTER_VERTICAL);
        paletteRow.setPadding(8, 5, 8, 5);
        paletteRow.setBackgroundColor(Color.WHITE);
        TextView colorsTitle = new TextView(this);
        colorsTitle.setText("Cores");
        colorsTitle.setTextColor(0xFF30354F);
        colorsTitle.setTypeface(null, Typeface.BOLD);
        paletteRow.addView(colorsTitle);
        HorizontalScrollView colorScroll = new HorizontalScrollView(this);
        colorScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout colors = new LinearLayout(this);
        colors.setPadding(8, 4, 8, 4);
        for (int color : palette) {
            View swatch = new View(this);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(color == selectedColor ? 5 : 2, color == selectedColor ? 0xFF5C6BC0 : 0xFFDDDFE8);
            swatch.setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(34,34);
            p.setMargins(5,0,5,0);
            colors.addView(swatch,p);
            swatch.setOnClickListener(v -> {
                selectedColor = color;
                drawing.setColor(color);
                drawing.setTool(DrawingView.TOOL_PEN);
                toolLabel.setText("Ferramenta: Lápis");
                refreshPalette(colors);
            });
        }
        colorScroll.addView(colors);
        paletteRow.addView(colorScroll, new LinearLayout.LayoutParams(0, 48, 1));
        root.addView(paletteRow);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(5, 5, 5, 8);
        footer.setBackgroundColor(Color.WHITE);
        addAction(footer, "↶", "Desfazer", () -> drawing.undo());
        addAction(footer, "↷", "Refazer", () -> drawing.redo());
        addAction(footer, "Limpar", "Tela", () -> new AlertDialog.Builder(this)
            .setMessage("Apagar todo o desenho?").setNegativeButton("Cancelar",null)
            .setPositiveButton("Apagar",(d,w)->drawing.clear()).show());
        addAction(footer, "Salvar", "PNG", () -> saveImage());
        addAction(footer, "☆", "Galeria", () -> saveToGallery());
        root.addView(footer);

        toolLabel = new TextView(this);
        toolLabel.setText("Ferramenta: Lápis");
        toolLabel.setTextColor(0xFF626984);
        toolLabel.setTextSize(12);
        toolLabel.setPadding(14, 2, 14, 6);
        root.addView(toolLabel);

        setContentView(root);
        updateLayerInfo(layerInfo);
    }

    private void showBrushPicker() {
        String[] options = {"Lápis clássico", "Marcador", "Aquarela suave", "Giz", "Spray"};
        new AlertDialog.Builder(this).setTitle("Escolher pincel")
            .setItems(options, (dialog, which) -> {
                drawing.setBrushType(which);
                drawing.setTool(DrawingView.TOOL_PEN);
                toolLabel.setText("Pincel: " + options[which]);
            }).show();
    }

    private File getGalleryDirectory() {
        File dir = new File(getFilesDir(), "galeria");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private void saveBitmapFile(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        }
    }

    private void saveToGallery() {
        Bitmap bitmap = drawing.exportBitmap();
        if (bitmap == null) {
            Toast.makeText(this, "Memória insuficiente para salvar.", Toast.LENGTH_LONG).show();
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(getGalleryDirectory(), "TracoLivre_" + stamp + ".png");
        try {
            saveBitmapFile(bitmap, file);
            Toast.makeText(this, "Desenho guardado na Galeria!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível guardar o desenho.", Toast.LENGTH_LONG).show();
        } finally {
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void showGallery() {
        File[] files = getGalleryDirectory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null || files.length == 0) {
            new AlertDialog.Builder(this).setTitle("Minha galeria")
                .setMessage("Ainda não há desenhos guardados. Use Salvar na Galeria para guardar uma cópia.")
                .setPositiveButton("Entendi", null).show();
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(this).setTitle("Minha galeria — abrir desenho")
            .setItems(names, (dialog, which) -> {
                Bitmap bitmap = BitmapFactory.decodeFile(files[which].getAbsolutePath());
                if (bitmap != null) {
                    drawing.clear();
                    drawing.setBackgroundBitmap(bitmap);
                    Toast.makeText(this, "Desenho aberto como referência.", Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this, "Não foi possível abrir esse desenho.", Toast.LENGTH_LONG).show();
            }).setNegativeButton("Fechar", null).show();
    }

    private File autosaveFile() { return new File(getFilesDir(), "autosave.png"); }

    private void saveAutosave() {
        if (drawing == null || drawing.getWidth() <= 0 || drawing.getHeight() <= 0) return;
        Bitmap bitmap = drawing.exportBitmap();
        if (bitmap == null) return;
        try { saveBitmapFile(bitmap, autosaveFile()); }
        catch (Exception ignored) { }
        finally { if (!bitmap.isRecycled()) bitmap.recycle(); }
    }

    private void restoreAutosave() {
        File file = autosaveFile();
        if (!file.exists()) return;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) drawing.setBackgroundBitmap(bitmap);
    }

    @Override protected void onPause() {
        saveAutosave();
        super.onPause();
    }

    private void updateLayerInfo(TextView info) {
        String visibility = drawing.isActiveLayerVisible() ? "visível" : "oculta";
        info.setText(drawing.getActiveLayerName() + "  •  "
                + drawing.getLayerCount() + " camada(s)  •  " + visibility);
    }

    private void showLayerManager(TextView layerInfo) {
        String[] names = drawing.getLayerNames();
        final int[] selected = {drawing.getActiveLayer()};
        new AlertDialog.Builder(this)
                .setTitle("Selecionar camada")
                .setSingleChoiceItems(names, selected[0], (dialog, which) -> selected[0] = which)
                .setPositiveButton("Selecionar", (dialog, which) -> {
                    drawing.setActiveLayer(selected[0]);
                    updateLayerInfo(layerInfo);
                })
                .setNeutralButton("Renomear", (dialog, which) -> {
                    drawing.setActiveLayer(selected[0]);
                    updateLayerInfo(layerInfo);
                    showRenameLayerDialog(layerInfo);
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showRenameLayerDialog(TextView layerInfo) {
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setText(drawing.getActiveLayerName());
        nameInput.setSelection(nameInput.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Renomear camada")
                .setView(nameInput)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    drawing.renameActiveLayer(nameInput.getText().toString());
                    updateLayerInfo(layerInfo);
                })
                .show();
    }

    private void refreshPalette(LinearLayout colors) {
        for (int i=0;i<colors.getChildCount();i++) {
            View v=colors.getChildAt(i);
            GradientDrawable bg=new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL); bg.setColor(palette[i]);
            bg.setStroke(palette[i]==selectedColor?5:2, palette[i]==selectedColor?0xFF5C6BC0:0xFFDDDFE8);
            v.setBackground(bg);
        }
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        return b;
    }
    private void addTool(LinearLayout row, String name, int tool) {
        Button b=button(name);
        b.setOnClickListener(v -> {
            if (tool == DrawingView.TOOL_TEXT) {
                EditText input = new EditText(this);
                input.setHint("Digite seu texto");
                new AlertDialog.Builder(this)
                    .setTitle("Adicionar texto")
                    .setView(input)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Adicionar", (dialog, which) -> {
                        String value = input.getText().toString().trim();
                        if (!value.isEmpty()) {
                            drawing.setPendingText(value);
                            drawing.setTool(DrawingView.TOOL_TEXT);
                            toolLabel.setText("Ferramenta: Texto — toque na tela");
                        }
                    }).show();
                return;
            }
            drawing.setTool(tool);
            if(tool==DrawingView.TOOL_ERASER) toolLabel.setText("Ferramenta: Borracha");
            else if(tool==DrawingView.TOOL_FILL) toolLabel.setText("Ferramenta: Balde de tinta — toque numa área fechada");
            else toolLabel.setText("Ferramenta: "+name);
        });
        row.addView(b);
    }
    private void addAction(LinearLayout row, String symbol, String label, Runnable action) {
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        TextView top=new TextView(this); top.setText(symbol); top.setTextSize(19);
        top.setTextColor(0xFF394264); top.setGravity(Gravity.CENTER);
        TextView bottom=new TextView(this); bottom.setText(label); bottom.setTextSize(10);
        bottom.setTextColor(0xFF626984);
        item.addView(top); item.addView(bottom);
        item.setPadding(10,2,10,2);
        item.setOnClickListener(v->action.run());
        row.addView(item,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
    }

    private void openImage() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_OPEN);
    }
    private void saveImage() {
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_TITLE,"Meu desenho.png");
        startActivityForResult(i,REQ_SAVE);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try {
            if(requestCode==REQ_OPEN) {
                Bitmap bitmap=decodeImage(uri);
                if(bitmap!=null) {
                    drawing.setBackgroundBitmap(bitmap);
                    Toast.makeText(this,"Imagem adicionada à tela!",Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,"Não foi possível abrir essa imagem.",Toast.LENGTH_LONG).show();
                }
            } else if(requestCode==REQ_SAVE) {
                Bitmap bitmap=drawing.exportBitmap();
                if(bitmap==null) {
                    Toast.makeText(this,"Memória insuficiente para exportar.",Toast.LENGTH_LONG).show();
                    return;
                }
                try (OutputStream out=getContentResolver().openOutputStream(uri)) {
                    if(out==null) throw new java.io.IOException("Destino indisponível");
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
                    out.flush();
                    Toast.makeText(this,"Desenho salvo com sucesso!",Toast.LENGTH_LONG).show();
                } finally {
                    if(!bitmap.isRecycled()) bitmap.recycle();
                }
            }
        } catch(Exception e) {
            Toast.makeText(this,"Não foi possível concluir a operação.",Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decodeImage(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            BitmapFactory.decodeStream(input, null, bounds);
        }

        int requestedWidth = Math.max(1, drawing.getWidth());
        int requestedHeight = Math.max(1, drawing.getHeight());
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= requestedWidth
                && bounds.outHeight / (sampleSize * 2) >= requestedHeight) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) return null;
            return BitmapFactory.decodeStream(input, null, options);
        } catch (OutOfMemoryError error) {
            return null;
        }
    }
}
