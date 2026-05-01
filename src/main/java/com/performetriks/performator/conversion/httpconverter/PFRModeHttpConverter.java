package com.performetriks.performator.conversion.httpconverter;

import javax.swing.SwingUtilities;

import com.performetriks.performator.base.PFRCustomMode;

public class PFRModeHttpConverter implements PFRCustomMode {

	@Override
	public String getUniqueName() {
		return "httpconverter";
	}

	@Override
	public void execute() {
		SwingUtilities.invokeLater(() -> {
			PFRHttpConverter g = new PFRHttpConverter();
			g.setVisible(true);
		});
	}

}
