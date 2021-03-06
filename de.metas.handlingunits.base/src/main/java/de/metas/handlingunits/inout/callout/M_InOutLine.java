package de.metas.handlingunits.inout.callout;

/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import org.adempiere.ad.callout.annotations.Callout;
import org.adempiere.ad.callout.annotations.CalloutMethod;
import org.adempiere.ad.callout.api.ICalloutField;
import org.adempiere.util.Services;
import org.compiere.model.I_M_InOut;

import de.metas.adempiere.gui.search.IHUPackingAwareBL;
import de.metas.adempiere.gui.search.impl.InOutLineHUPackingAware;
import de.metas.handlingunits.inout.IHUInOutBL;
import de.metas.handlingunits.model.I_M_InOutLine;

@Callout(I_M_InOutLine.class)
public class M_InOutLine
{
	public static final M_InOutLine instance = new M_InOutLine();

	@CalloutMethod(columnNames = {
			I_M_InOutLine.COLUMNNAME_IsManualPackingMaterial,
			I_M_InOutLine.COLUMNNAME_QtyTU_Calculated,
			I_M_InOutLine.COLUMNNAME_QtyTU_Override,
			I_M_InOutLine.COLUMNNAME_QtyEnteredTU,
			I_M_InOutLine.COLUMNNAME_C_UOM_ID,
			I_M_InOutLine.COLUMNNAME_M_HU_PI_Item_Product_Calculated_ID,
			I_M_InOutLine.COLUMNNAME_M_HU_PI_Item_Product_Override_ID,
			I_M_InOutLine.COLUMNNAME_M_HU_PI_Item_Product_ID

	})
	private void updateShipmentLineQtyEnteredTU(final I_M_InOutLine shipmentLine, final ICalloutField field)
	{
		if (shipmentLine == null)
		{
			return;
		}

		// Make sure we are dealing with a shipment (and not with a receipt) which is about real products and not about packing materials
		if (!isNonPackingMaterialShipment(shipmentLine))
		{
			return;
		}

		//
		// Make sure our effective values are up2date
		Services.get(IHUInOutBL.class).updateEffectiveValues(shipmentLine);

		// Update QtyCU, but only if we deal with Manual Packing Materials,
		// else don't touch it!
		if (shipmentLine.isManualPackingMaterial())
		{
			//
			// Calculate and set QtyEntered(CU) from M_HU_PI_Item_Product and QtyEnteredTU(aka QtyPacks)
			final IHUPackingAwareBL huPackingAwareBL = Services.get(IHUPackingAwareBL.class);
			final InOutLineHUPackingAware packingAware = new InOutLineHUPackingAware(shipmentLine);
			final int qtyTU = packingAware.getQtyPacks().intValueExact();
			huPackingAwareBL.setQty(packingAware, qtyTU);
		}
	}

	/**
	 *
	 * @param inoutLine
	 * @return true if it's a shipment line and it's NOT about packing materials
	 */
	private final boolean isNonPackingMaterialShipment(final I_M_InOutLine inoutLine)
	{

		// make sure the shipment is not null
		final I_M_InOut shipment = inoutLine.getM_InOut();

		if (shipment == null)
		{
			return false;
		}

		// Make sure we are dealing with a shipment (and not with a receipt)

		if (!shipment.isSOTrx())
		{
			return false;
		}

		// Do nothing if we are dealing with a packing material line
		if (inoutLine.isPackagingMaterial())
		{
			return false;
		}

		return true;
	}
}
