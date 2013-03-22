package ar.app;

import javax.swing.*;

import ar.Aggregates;
import ar.app.util.AggregatesToJSON;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.NoninvertibleTransformException;

public class ARApp {
	private ARPanel<?,?> image;
	private final JFrame frame = new JFrame();
	private final JComboBox<WrappedTransfer<?>> transfers = new JComboBox<WrappedTransfer<?>>();
	private final JComboBox<WrappedReduction<?>> reductions = new JComboBox<WrappedReduction<?>>();
	
	private final JComboBox<Dataset> dataset = new JComboBox<Dataset>();
	private final JButton export = new JButton("Export");

	
	public ARApp() {
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setTitle("Abstract Rendering Demo App");
		frame.setLayout(new BorderLayout());
		

		JPanel controls = new JPanel();
//		controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
		JPanel outer = new JPanel();
		outer.setLayout(new BorderLayout());
		outer.add(controls, BorderLayout.WEST);
		frame.add(outer, BorderLayout.SOUTH);
		
		controls.add(dataset);
		controls.add(reductions);
		controls.add(transfers);
		controls.add(export);
		final ARApp app = this;
		
		reductions.addItem(new WrappedReduction.SolidBlue());
		reductions.addItem(new WrappedReduction.OverplotFirst());
		reductions.addItem(new WrappedReduction.OverplotLast());
		reductions.addItem(new WrappedReduction.Count());
		
		transfers.addItem(new WrappedTransfer.EchoColor());
		transfers.addItem(new WrappedTransfer.RedWhiteInterpolate());
		transfers.addItem(new WrappedTransfer.RedBlueInterpolate());
		transfers.addItem(new WrappedTransfer.OutlierHighlight());
		
		dataset.addItem(new Dataset.SyntheticScatterplot());
		dataset.addItem(new Dataset.Memory());
		dataset.addItem(new Dataset.MPIPhases());
		
		dataset.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Dataset glyphs = (Dataset) dataset.getSelectedItem();
				app.changeImage(image.withDataset(glyphs));
			}});
		
		reductions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				WrappedReduction<?> r = (WrappedReduction<?>) reductions.getSelectedItem();
				app.changeImage(image.withReduction(r));
			}});
		
		transfers.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				WrappedTransfer<?> t = (WrappedTransfer<?>) transfers.getSelectedItem();
				app.changeImage(image.withTransfer(t));
			}});
	
		
		export.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				 JFileChooser fd = new JFileChooser("Export Aggregates (e.g., reduction results)");
				 int returnVal = fd.showDialog(frame, "Export");
				 if (returnVal == JFileChooser.APPROVE_OPTION) {
					 AggregatesToJSON.export(image.getAggregates(),fd.getSelectedFile());
				 }
			}
			
		});
		
		image = new ARPanel(((WrappedReduction) reductions.getSelectedItem()), 
							((WrappedTransfer) transfers.getSelectedItem()), 
							(Dataset) dataset.getSelectedItem());
		
		frame.add(image, BorderLayout.CENTER);

		frame.setSize(400, 200);
		frame.invalidate();
		frame.setVisible(true);
	}
	
	public <A,B> void changeImage(ARPanel<A,B> newImage) {
		JPanel old = this.image;		
		frame.remove(old);
		frame.add(newImage, BorderLayout.CENTER);
		this.image = newImage;
		frame.revalidate();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new ARApp();
	}

}
