package de.metas.inoutcandidate.api;

/*
 * #%L
 * de.metas.swat.base
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


import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Packaging source; something that will be later packed by picking terminal
 * 
 * @author tsa
 * 
 */
public interface IPackageable
{
	int getBPartnerId();

	String getProductName();

	int getProductId();

	String getDocType();

	String getFreightCostRule();

	Timestamp getDeliveryDate();

	String getDocumentNo();

	boolean isDisplayed();

	String getShipperName();

	String getDeliveryVia();

	int getWarehouseDestId();

	String getWarehouseDestName();

	String getWarehouseName();

	String getBPartnerLocationName();

	String getBPartnerName();

	String getBPartnerValue();

	BigDecimal getQtyToDeliver();

	int getShipmentScheduleId();

	int getBPartnerLocationId();

	String getBPartnerAddress();

//	String getDeliveryViaName();

	int getWarehouseId();

	/**
	 * 
	 * @return C_Order_ID
	 */
	int getOrderId();

	/**
	 * 
	 * @return M_Shipper_ID
	 */
	int getShipperId();

	String getDocSubType();

//	String getFreightCostRuleName();

	Timestamp getPreparationDate();
}
