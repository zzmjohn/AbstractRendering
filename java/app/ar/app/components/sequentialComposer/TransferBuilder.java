package ar.app.components.sequentialComposer;

import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JPanel;

import ar.Transfer;
import ar.app.util.ActionProvider;
import ar.app.util.AppUtil;
import ar.util.HasViewTransform;

@SuppressWarnings("rawtypes")
public class TransferBuilder extends JPanel {
	private final ActionProvider actionProvider = new ActionProvider("Transfer Changed");  
	private final List<JComboBox<OptionTransfer>> transferLists = new ArrayList<>();
	private final List<OptionTransfer.ControlPanel> optionPanels = new ArrayList<>();
	private final HasViewTransform transferProvider;

	public TransferBuilder(HasViewTransform transferProvider) {
		this.transferProvider = transferProvider;
		this.setLayout(new GridLayout(0,2));
		addTransferBox();
	}
		
	public void addActionListener(ActionListener l) {actionProvider.addActionListener(l);}
	
	public void configureTo(final List<OptionTransfer> transfers) {
		transferLists.clear();
		optionPanels.clear();
		
		for (int i=0; i<transfers.size(); i++) {
			addTransferBox();
			JComboBox<OptionTransfer> b = transferLists.get(i);
			b.setSelectedItem(transfers.get(i));
		}
		//The standard "extra" box at the end is added by a state change listener
		rebuild();
	}
	
	public Transfer<?,?> transfer() {
		List<OptionTransfer> transfers = new ArrayList<>();
		for (JComboBox<OptionTransfer> tl: transferLists) {
			int idx = tl.getSelectedIndex();
			OptionTransfer ot = tl.getItemAt(idx);
			transfers.add(ot);
		}
		return OptionTransfer.toTransfer(transfers, optionPanels);
	}
	
	private void rebuild() {
		this.removeAll();
		for (int i=0; i<transferLists.size();i++) {
			this.add(transferLists.get(i));
			this.add(optionPanels.get(i));
		}		
		revalidate();
		if (this.getParent() != null) {this.getParent().revalidate();}
	}
	
	private void addTransferBox() {
		JComboBox<OptionTransfer> transfers = new JComboBox<OptionTransfer>();
		AppUtil.loadInstances(transfers, OptionTransfer.class, OptionTransfer.class, OptionTransfer.Echo.NAME);
		transfers.addItemListener(new ChangeTransfer(this));
		
		transferLists.add(transfers);
		
		OptionTransfer.ControlPanel controls = transfers.getItemAt(transfers.getSelectedIndex()).control(transferProvider);
		optionPanels.add(controls);
		controls.addActionListener(actionProvider.actionDelegate());
		
		rebuild();
	}
	
	public static final class ChangeTransfer implements ItemListener {
		final TransferBuilder host;
		
		public ChangeTransfer(TransferBuilder host) {
			this.host = host;
		}
		
		@Override
		@SuppressWarnings("unchecked")
		public void itemStateChanged(ItemEvent e) {
			int size = host.transferLists.size();
			JComboBox<OptionTransfer> transferList = (JComboBox<OptionTransfer>) e.getSource();
			int idx = host.transferLists.indexOf(transferList);
			boolean end = transferList.getSelectedItem().toString().equals(OptionTransfer.Echo.NAME);
			
			if (idx < size-1 && end) {
				host.transferLists.remove(idx);
				host.optionPanels.remove(idx);
				host.rebuild();
			} else if (idx == size-1 && !end) {				
				host.addTransferBox();
			} else {
				OptionTransfer.ControlPanel params = transferList.getItemAt(transferList.getSelectedIndex()).control(host.transferProvider);
				host.optionPanels.remove(idx);
				host.optionPanels.add(idx, params);
				params.addActionListener(host.actionProvider.actionDelegate());
				host.rebuild();
			}
			host.actionProvider.fireActionListeners();
		}
	}
}
