/*******************************************************************************
 * Copyright (c) 2011 JVM Monitor project. All rights reserved. 
 * 
 * This code is distributed under the terms of the Eclipse Public License v1.0
 * which is available at http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.jvmmonitor.internal.ui.properties.memory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.jvmmonitor.core.IActiveJvm;
import org.jvmmonitor.core.JvmCoreException;
import org.jvmmonitor.internal.ui.RefreshJob;
import org.jvmmonitor.internal.ui.actions.PreferencesAction;
import org.jvmmonitor.internal.ui.actions.RefreshAction;
import org.jvmmonitor.internal.ui.actions.ToggleOrientationAction;
import org.jvmmonitor.internal.ui.properties.AbstractSashForm;
import org.jvmmonitor.internal.ui.properties.StackTraceViewer;
import org.jvmmonitor.ui.Activator;

public class SWTResourcesPage extends AbstractSashForm {

    /** The sash weights. */
    private static final int[] SASH_WEIGHTS = new int[] { 45, 55 };

    /** The layout menu id. */
    private static final String LAYOUT_MENU_ID = "layout"; //$NON-NLS-1$

    /** The resource viewer. */
    TreeViewer resourceViewer;

    /** The stack trace viewer. */
    StackTraceViewer stackTraceViewer;

    /** The memory section. */
    MemorySection section;

    /** The separator. */
    private Separator separator;

    /** The action to refresh section. */
    RefreshAction refreshAction;

    /** The action to clear SWT resource action. */
    ClearSWTResourceAction clearSWTResourceAction;

    /** The layout menu. */
    private MenuManager layoutMenu;

    /**
     * The constructor.
     * 
     * @param tabFolder
     *            The tab folder
     * @param actionBars
     *            The action bars
     */
    public SWTResourcesPage(MemorySection section, final CTabFolder tabFolder,
            IActionBars actionBars) {
        super(tabFolder, actionBars, SASH_WEIGHTS);
        this.section = section;

        createSashFormControls(this, actionBars);
        setWeights(initialSashWeights);

        createActions();

        final CTabItem tabItem = section.getWidgetFactory().createTabItem(
                tabFolder, SWT.NONE);
        tabItem.setText(Messages.swtResourcesLabel);
        tabItem.setControl(this);

        tabFolder.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                boolean tabSelected = tabFolder.getSelection().equals(tabItem);
                refreshBackground();
                updateLocalToolBar(tabSelected);
                updateLocalMenus(tabSelected);
            }
        });
    }

    /*
     * @see AbstractSashForm#createSashFormControls(SashForm, IActionBars)
     */
    @Override
    protected void createSashFormControls(SashForm sashForm,
            IActionBars actionBars) {
        resourceViewer = new SWTResourceFilteredTree(sashForm).getViewer();
        resourceViewer.setContentProvider(new SWTResourceContentProvider(
                resourceViewer));
        resourceViewer.setLabelProvider(new LabelProvider());
        resourceViewer
                .addSelectionChangedListener(new ISelectionChangedListener() {
                    @Override
                    public void selectionChanged(SelectionChangedEvent event) {
                        ISelection selection = event.getSelection();
                        if (selection.isEmpty()) {
                            selection = null;
                        }
                        stackTraceViewer.setInput(selection);
                    }
                });

        stackTraceViewer = new StackTraceViewer(sashForm, actionBars);
    }

    /**
     * Refreshes the appearance.
     * 
     * @param force
     *            <tt>true</tt> to force refresh
     */
    public void refresh(final boolean force) {
        new RefreshJob(NLS.bind(Messages.refreshMemorySectionJobLabel, section
                .getJvm().getPid()), section.getId()) {
            @Override
            protected void refreshModel(IProgressMonitor monitor) {
                try {
                    IActiveJvm jvm = section.getJvm();
                    if (jvm != null && jvm.isConnected()
                            && !section.isRefreshSuspended()) {
                        if (jvm.getSWTResourceMonitor().isSupported()) {
                            jvm.getSWTResourceMonitor().refreshResourcesCache();
                        }
                    }
                } catch (JvmCoreException e) {
                    Activator.log(Messages.refreshHeapDataFailedMsg, e);
                }
            }

            @Override
            protected void refreshUI() {
                refreshBackground();

                if (!force && section.isRefreshSuspended()) {
                    return;
                }

                if (!resourceViewer.getControl().isDisposed()) {
                    resourceViewer.refresh();

                    // select the first item if no item is selected
                    if (resourceViewer.getSelection().isEmpty()) {
                        TreeItem[] items = resourceViewer.getTree().getItems();
                        if (items != null && items.length > 0) {
                            resourceViewer.getTree().select(items[0]);
                            stackTraceViewer.setInput(resourceViewer
                                    .getSelection());
                        } else {
                            stackTraceViewer.setInput(null);
                        }
                    }
                }
                if (!stackTraceViewer.getControl().isDisposed()) {
                    stackTraceViewer.refresh();
                }
            }
        }.schedule();
    }

    /**
     * Sets the SWT resource input.
     * 
     * @param input
     *            The SWT resource input
     */
    public void setInput(ISWTResorceInput input) {
        if (!section.isRefreshSuspended()) {
            resourceViewer.setInput(input);
        }
    }

    /**
     * Refreshes the background.
     */
    void refreshBackground() {
        IActiveJvm jvm = section.getJvm();
        boolean isConnected = jvm != null && jvm.isConnected();
        section.refreshBackground(getChildren(), isConnected);
    }

    /**
     * Updates the local tool bar.
     * 
     * @param activated
     *            <tt>true</tt> if this tab item is activated
     */
    void updateLocalToolBar(boolean activated) {
        IToolBarManager manager = section.getActionBars().getToolBarManager();
        if (activated) {
            addToolBarActions(manager);
        } else {
            removeToolBarActions(manager);
        }
    }

    /**
     * Updates the local menus.
     * 
     * @param activated
     *            <tt>true</tt> if this tab item is activated
     */
    void updateLocalMenus(boolean activated) {
        IMenuManager manager = section.getActionBars().getMenuManager();
        if (activated) {
            addLocalMenus(manager);
        } else {
            removeLocalMenus(manager);
        }
    }

    /**
     * Adds the tool bar actions.
     * 
     * @param manager
     *            The tool bar manager
     */
    void addToolBarActions(IToolBarManager manager) {
        manager.insertAfter("defaults", separator); //$NON-NLS-1$
        if (manager.find(refreshAction.getId()) == null) {
            manager.insertAfter("defaults", refreshAction); //$NON-NLS-1$
        }
        if (manager.find(clearSWTResourceAction.getId()) == null) {
            manager.insertAfter("defaults", clearSWTResourceAction); //$NON-NLS-1$
        }
        manager.update(true);
    }

    /**
     * Removes the tool bar actions.
     * 
     * @param manager
     *            The tool bar manager
     */
    void removeToolBarActions(IToolBarManager manager) {
        manager.remove(separator);
        manager.remove(refreshAction.getId());
        manager.remove(clearSWTResourceAction.getId());
        manager.update(true);
    }

    /**
     * Adds the local menus.
     * 
     * @param manager
     *            The menu manager
     */
    void addLocalMenus(IMenuManager manager) {
        if (manager.find(layoutMenu.getId()) == null) {
            if (manager.find(PreferencesAction.class.getName()) != null) {
                manager.insertBefore(PreferencesAction.class.getName(),
                        layoutMenu);
            } else {
                manager.add(layoutMenu);
            }

            for (ToggleOrientationAction action : getOrientationActions()) {
                if (layoutMenu.find(action.getId()) == null) {
                    layoutMenu.add(action);
                }
            }
        }
    }

    /**
     * Removes the local menus.
     * 
     * @param manager
     *            The menu manager
     */
    void removeLocalMenus(IMenuManager manager) {
        manager.remove(layoutMenu);
    }

    /**
     * Creates the actions.
     */
    private void createActions() {
        refreshAction = new RefreshAction(section) {
            // to have different action id from one in heap histogram
        };
        clearSWTResourceAction = new ClearSWTResourceAction(this, section);
        separator = new Separator();
        layoutMenu = new MenuManager(Messages.layoutLabel, LAYOUT_MENU_ID);
    }
}
