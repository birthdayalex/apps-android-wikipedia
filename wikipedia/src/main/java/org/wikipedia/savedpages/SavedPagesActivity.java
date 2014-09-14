package org.wikipedia.savedpages;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.view.ActionMode;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.squareup.picasso.Picasso;
import org.wikipedia.R;
import org.wikipedia.ThemedActionBarActivity;
import org.wikipedia.WikipediaApp;
import org.wikipedia.history.HistoryEntry;
import org.wikipedia.page.PageActivity;
import org.wikipedia.pageimages.PageImage;

import java.util.ArrayList;

public class SavedPagesActivity extends ThemedActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final int ACTIVITY_RESULT_SAVEDPAGE_SELECT = 1;

    private ListView savedPagesList;
    private View savedPagesEmptyContainer;
    private TextView savedPagesEmptyTitle;
    private TextView savedPagesEmptyMessage;
    private SavedPagesAdapter adapter;
    private RefreshPagesHandler refreshHandler;
    private EditText entryFilter;
    private ImageView savedPagesEmptyImage;

    private WikipediaApp app;

    private ActionMode actionMode;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (WikipediaApp)getApplicationContext();

        setContentView(R.layout.activity_saved_pages);
        savedPagesList = (ListView) findViewById(R.id.saved_pages_list);
        savedPagesEmptyContainer = findViewById(R.id.saved_pages_empty_container);
        savedPagesEmptyTitle = (TextView) findViewById(R.id.saved_pages_empty_title);
        savedPagesEmptyMessage = (TextView) findViewById(R.id.saved_pages_empty_message);
        entryFilter = (EditText) findViewById(R.id.saved_pages_search_list);
        savedPagesEmptyImage = (ImageView) findViewById(R.id.saved_pages_empty_image);

        adapter = new SavedPagesAdapter(this, null, true);
        savedPagesList.setAdapter(adapter);
        savedPagesList.setEmptyView(savedPagesEmptyContainer);

        savedPagesList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (actionMode != null) {
                    return false;
                }
                savedPagesList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                actionMode = startSupportActionMode(new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        mode.getMenuInflater().inflate(R.menu.menu_saved_pages_context, menu);
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.menu_refresh_selected_saved_pages:
                                refreshSelected();
                                actionMode.finish();
                                return true;
                            case R.id.menu_delete_selected_saved_pages:
                                deleteSelected();
                                actionMode.finish();
                                return true;
                            default:
                                // This can't happen
                                throw new RuntimeException("Unknown context menu item clicked");
                        }
                    }


                    private void deleteSelected() {
                        SparseBooleanArray checkedItems = savedPagesList.getCheckedItemPositions();
                        for (int i = 0; i < checkedItems.size(); i++) {
                            if (checkedItems.valueAt(i)) {
                                final SavedPage page = SavedPage.PERSISTANCE_HELPER.fromCursor((Cursor) adapter.getItem(checkedItems.keyAt(i)));
                                new DeleteSavedPageTask(SavedPagesActivity.this, page) {
                                    @Override
                                    public void onFinish(Boolean result) {
                                        Toast.makeText(SavedPagesActivity.this, R.string.toast_saved_page_deleted, Toast.LENGTH_SHORT).show();
                                    }
                                }.execute();
                            }
                        }
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {
                        savedPagesList.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
                        actionMode = null;
                        // Clear all selections
                        savedPagesList.clearChoices();
                        savedPagesList.requestLayout(); // Required to immediately redraw unchecked states

                    }
                });
                savedPagesList.setItemChecked(position, true);
                return true;
            }
        });

        entryFilter.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        // Do nothing
                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                        // Do nothing
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {
                        getSupportLoaderManager().restartLoader(0, null, SavedPagesActivity.this);
                        if (editable.length() == 0) {
                            savedPagesEmptyTitle.setText(R.string.saved_pages_empty_title);
                            savedPagesEmptyImage.setVisibility(View.VISIBLE);
                            savedPagesEmptyMessage.setVisibility(View.VISIBLE);
                        } else {
                            savedPagesEmptyTitle.setText(getString(R.string.saved_pages_search_empty_message, editable.toString()));
                            savedPagesEmptyImage.setVisibility(View.GONE);
                            savedPagesEmptyMessage.setVisibility(View.GONE);
                        }
                    }
                });

        savedPagesList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // We shouldn't do anything if the user is multi-selecting things
                if (actionMode == null) {
                    SavedPage savedPage = (SavedPage) view.getTag();
                    HistoryEntry newEntry = new HistoryEntry(savedPage.getTitle(), HistoryEntry.SOURCE_SAVED_PAGE);

                    Intent intent = new Intent();
                    intent.setClass(SavedPagesActivity.this, PageActivity.class);
                    intent.setAction(PageActivity.ACTION_PAGE_FOR_TITLE);
                    intent.putExtra(PageActivity.EXTRA_PAGETITLE, savedPage.getTitle());
                    intent.putExtra(PageActivity.EXTRA_HISTORYENTRY, newEntry);
                    setResult(ACTIVITY_RESULT_SAVEDPAGE_SELECT, intent);
                    finish();
                }
            }
        });

        getSupportLoaderManager().initLoader(0, null, this);
        app.adjustDrawableToTheme(savedPagesEmptyImage.getDrawable());
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String selection = null;
        String[] selectionArgs = null;
        savedPagesEmptyContainer.setVisibility(View.GONE);
        String searchStr = entryFilter.getText().toString();
        if (searchStr.length() != 0) {
            // FIXME: Find ways to not have to hard code column names
            searchStr = searchStr.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            selection =  "UPPER(savedpages.title) LIKE UPPER(?) ESCAPE '\\'";
            selectionArgs = new String[]{"%" + searchStr + "%"};
        }
        return new CursorLoader(
                this,
                Uri.parse(SavedPage.PERSISTANCE_HELPER.getBaseContentURI().toString() + "/" + PageImage.PERSISTANCE_HELPER.getTableName()),
                null,
                selection,
                selectionArgs,
                "savedpages.title ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoaderLoader, Cursor cursorLoader) {
        adapter.swapCursor(cursorLoader);
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoaderLoader) {
        adapter.changeCursor(null);
    }

    private class SavedPagesAdapter extends CursorAdapter {
        public SavedPagesAdapter(Context context, Cursor c, boolean autoRequery) {
            super(context, c, autoRequery);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            return getLayoutInflater().inflate(R.layout.item_saved_page_entry, viewGroup, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView title = (TextView) view.findViewById(R.id.saved_page_title);
            ImageView thumbnail = (ImageView) view.findViewById(R.id.saved_page_thumbnail);
            SavedPage entry = SavedPage.PERSISTANCE_HELPER.fromCursor(cursor);
            title.setText(entry.getTitle().getDisplayText());
            view.setTag(entry);

            Picasso.with(SavedPagesActivity.this)
                    .load(cursor.getString(SavedPageContentProvider.COL_INDEX_IMAGE))
                    .placeholder(R.drawable.ic_pageimage_placeholder)
                    .error(R.drawable.ic_pageimage_placeholder)
                    .into(thumbnail);

            // If this page title's first letter is different from the previous title's
            // first letter, then display the heading.
            String curLetter, prevLetter = "";
            if (cursor.getPosition() != 0) {
                Cursor prevCursor = (Cursor) getItem(cursor.getPosition() - 1);
                SavedPage prevEntry = SavedPage.PERSISTANCE_HELPER.fromCursor(prevCursor);
                prevLetter = prevEntry.getTitle().getDisplayText().substring(0, 1);
            }
            curLetter = entry.getTitle().getDisplayText().substring(0, 1);
            TextView sectionHeader = (TextView) view.findViewById(R.id.saved_page_section_header_text);
            if (!curLetter.equals(prevLetter)) {
                sectionHeader.setText(curLetter);
                sectionHeader.setVisibility(View.VISIBLE);
            } else {
                sectionHeader.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_saved_pages, menu);
        app.adjustDrawableToTheme(menu.findItem(R.id.menu_refresh_all_saved_pages).getIcon());
        app.adjustDrawableToTheme(menu.findItem(R.id.menu_clear_all_saved_pages).getIcon());
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_clear_all_saved_pages).setVisible(savedPagesList.getCount() > 0);
        menu.findItem(R.id.menu_refresh_all_saved_pages).setVisible(savedPagesList.getCount() > 0);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.menu_refresh_all_saved_pages:
                promptToRefreshAll();
                return true;
            case R.id.menu_clear_all_saved_pages:
                promptToDeleteAll();
                return true;
            default:
                throw new RuntimeException("Unknown menu item clicked!");
        }
    }

    private void promptToRefreshAll() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_prompt_refresh_all_saved_pages);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                refreshAll();
            }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.create().show();
    }

    private void promptToDeleteAll() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.dialog_title_clear_saved_pages);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                new DeleteAllSavedPagesTask(SavedPagesActivity.this) {
                    @Override
                    public void onFinish(Void v) {
                        Toast.makeText(SavedPagesActivity.this, R.string.toast_saved_page_deleted, Toast.LENGTH_SHORT).show();
                    }
                }.execute();
            }
        });
        builder.setNegativeButton(R.string.no, null);
        builder.create().show();
    }

    private void refreshSelected() {
        SparseBooleanArray checkedItems = savedPagesList.getCheckedItemPositions();
        ArrayList<SavedPage> savedPages = new ArrayList<SavedPage>();
        for (int i = 0; i < checkedItems.size(); i++) {
            if (checkedItems.valueAt(i)) {
                SavedPage page = SavedPage.PERSISTANCE_HELPER.fromCursor((Cursor) adapter.getItem(checkedItems.keyAt(i)));
                savedPages.add(page);
            }
        }
        refreshHandler = new RefreshPagesHandler(SavedPagesActivity.this, savedPages);
        refreshHandler.refresh();
    }

    private void refreshAll() {
        ArrayList<SavedPage> savedPages = new ArrayList<SavedPage>();
        for (int i = 0; i < adapter.getCount(); i++) {
            SavedPage page = SavedPage.PERSISTANCE_HELPER.fromCursor((Cursor) adapter.getItem(i));
            savedPages.add(page);
        }
        refreshHandler = new RefreshPagesHandler(SavedPagesActivity.this, savedPages);
        refreshHandler.refresh();
    }

    @Override
    public void onStop() {
        if (refreshHandler != null) {
            refreshHandler.onStop();
        }
        super.onStop();
    }
}
