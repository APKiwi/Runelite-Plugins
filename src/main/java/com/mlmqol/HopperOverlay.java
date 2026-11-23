package com.mlmqol;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

class HopperOverlay extends Overlay
{
	private static final String STOPPED_TEXT = "Strut broken";
	private static final String IN_TRANSIT_TEXT = "Ore in transit";

	private final MlmQolPlugin plugin;
	private final MlmQolConfig config;

	@Inject
	HopperOverlay(MlmQolPlugin plugin, MlmQolConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.highlightBrokenStruts())
		{
			for (TileObject strut : plugin.getBrokenStruts())
			{
				Shape clickbox = strut.getClickbox();
				if (clickbox != null)
				{
					OverlayUtil.renderPolygon(graphics, clickbox, config.stoppedHopperColor());
				}
			}
		}

		if (!plugin.shouldFlagHopper())
		{
			return null;
		}

		List<String> texts = new ArrayList<>(2);
		List<Color> colors = new ArrayList<>(2);

		if (plugin.isMachineryStopped())
		{
			texts.add(STOPPED_TEXT);
			colors.add(config.stoppedHopperColor());
		}

		if (plugin.hasPayDirtInTransit())
		{
			texts.add(IN_TRANSIT_TEXT);
			colors.add(config.oreInTransitColor());
		}

		if (texts.isEmpty())
		{
			return null;
		}

		int lineHeight = texts.size() > 1 ? graphics.getFontMetrics().getHeight() : 0;

		for (TileObject hopper : plugin.getHoppers())
		{
			Shape clickbox = hopper.getClickbox();
			if (clickbox == null)
			{
				continue;
			}

			OverlayUtil.renderPolygon(graphics, clickbox, colors.get(0));

			for (int i = 0; i < texts.size(); i++)
			{
				String text = texts.get(i);
				Point location = hopper.getCanvasTextLocation(graphics, text, 0);
				if (location != null)
				{
					OverlayUtil.renderTextLocation(graphics, new Point(location.getX(), location.getY() + i * lineHeight), text, colors.get(i));
				}
			}
		}

		return null;
	}
}
