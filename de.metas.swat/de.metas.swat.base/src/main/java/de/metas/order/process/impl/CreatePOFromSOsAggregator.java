package de.metas.order.process.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.adempiere.bpartner.service.IBPartnerDAO;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.model.IContextAware;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.service.IOrgDAO;
import org.adempiere.util.ILoggable;
import org.adempiere.util.Services;
import org.adempiere.util.api.IMsgBL;
import org.adempiere.util.collections.MapReduceAggregator;
import org.compiere.model.I_AD_Org;
import org.compiere.model.I_AD_OrgInfo;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_Order;

import de.metas.adempiere.service.IOrderBL;
import de.metas.interfaces.I_C_OrderLine;

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

/**
 * Created new purchase orders for sales order lines and contains one instance of {@link CreatePOLineFromSOLinesAggregator} for each created purchase order line.
 *
 * @author metas-dev <dev@metas-fresh.com>
 *
 */
public class CreatePOFromSOsAggregator extends MapReduceAggregator<I_C_Order, I_C_OrderLine>
{
	private static final String MSG_PURCHASE_ORDER_CREATED = "de.metas.order.C_Order_CreatePOFromSOs.PurchaseOrderCreated";
	private final IContextAware context;
	private final boolean p_IsDropShip;

	private final I_C_Order dummyOrder;

	private final IBPartnerDAO bpartnerDAO = Services.get(IBPartnerDAO.class);
	final IMsgBL msgBL = Services.get(IMsgBL.class);

	final Map<String, CreatePOLineFromSOLinesAggregator> orderKey2OrderLineAggregator = new HashMap<>();

	public CreatePOFromSOsAggregator(final IContextAware context, final boolean p_IsDropShip)
	{
		this.context = context;
		this.p_IsDropShip = p_IsDropShip;

		dummyOrder = InterfaceWrapperHelper.newInstance(I_C_Order.class, context);
		dummyOrder.setDocumentNo(CreatePOFromSOsAggregationKeyBuilder.KEY_SKIP);
	}

	/**
	 * Creates a new purchase order.
	 */
	@Override
	protected I_C_Order createGroup(final Object vendorBPartnerValue, final I_C_OrderLine salesOrderLine)
	{
		if (CreatePOFromSOsAggregationKeyBuilder.KEY_SKIP.equals(vendorBPartnerValue))
		{
			return dummyOrder;
			// nothing to do;
		}

		final I_C_Order salesOrder = salesOrderLine.getC_Order();

		final I_C_BPartner vendor = bpartnerDAO.retrieveBPartnerByValue(context.getCtx(), (String)vendorBPartnerValue);

		final I_C_Order purchaseOrder = createPurchaseOrder(vendor, salesOrder);

		final ILoggable loggable = getLoggable();

		final String msg = msgBL.getMsg(context.getCtx(),
				MSG_PURCHASE_ORDER_CREATED,
				new Object[] { purchaseOrder.getDocumentNo() });
		loggable.addLog(msg);

		return purchaseOrder;
	}

	@Override
	protected void closeGroup(final I_C_Order purchaseOrder)
	{
		final CreatePOLineFromSOLinesAggregator orderLineAggregator = getCreateLineAggregator(purchaseOrder);
		orderLineAggregator.closeAllGroups();

		try
		{
			if (orderLineAggregator.getItemsCount() <= 0)
			{
				InterfaceWrapperHelper.delete(purchaseOrder);
			}
			else
			{
				InterfaceWrapperHelper.save(purchaseOrder);
			}
		}
		catch (Throwable t)
		{
			getLoggable().addLog("@Error@: " + t);
			throw AdempiereException.wrapIfNeeded(t);
		}
	}

	@Override
	protected void addItemToGroup(final I_C_Order purchaseOrder, final I_C_OrderLine salesOrderLine)
	{
		final CreatePOLineFromSOLinesAggregator orderLineAggregator = getCreateLineAggregator(purchaseOrder);
		if (orderLineAggregator.getPurchaseOrder() == dummyOrder)
		{
			return;// nothing to do
		}

		orderLineAggregator.add(salesOrderLine);

		final I_C_Order salesOrder = salesOrderLine.getC_Order();

		if (purchaseOrder.getLink_Order_ID() > 0 &&
				purchaseOrder.getLink_Order_ID() != salesOrder.getC_Order_ID())
		{
			purchaseOrder.setLink_Order(null);
		}
	}

	private CreatePOLineFromSOLinesAggregator getCreateLineAggregator(final I_C_Order pruchaseOrder)
	{
		CreatePOLineFromSOLinesAggregator orderLinesAggregator = orderKey2OrderLineAggregator.get(pruchaseOrder.getDocumentNo());
		if (orderLinesAggregator == null)
		{
			orderLinesAggregator = new CreatePOLineFromSOLinesAggregator(pruchaseOrder);
			orderLinesAggregator.setItemAggregationKeyBuilder(CreatePOLineFromSOLinesAggregationKeyBuilder.INSTANCE);
			orderLinesAggregator.setGroupsBufferSize(100);

			orderKey2OrderLineAggregator.put(pruchaseOrder.getDocumentNo(), orderLinesAggregator);
		}
		return orderLinesAggregator;
	}

	private I_C_Order createPurchaseOrder(final I_C_BPartner vendor,
			final I_C_Order salesOrder)
	{

		final I_C_Order purchaseOrder = InterfaceWrapperHelper.newInstance(I_C_Order.class, context);

		// services
		final IOrderBL orderBL = Services.get(IOrderBL.class);
		final IOrgDAO orgDAO = Services.get(IOrgDAO.class);

		final Properties ctx = InterfaceWrapperHelper.getCtx(salesOrder);
		final String trxName = InterfaceWrapperHelper.getTrxName(salesOrder);

		final I_AD_Org org = salesOrder.getAD_Org();

		purchaseOrder.setAD_Org(org);
		purchaseOrder.setLink_Order_ID(salesOrder.getC_Order_ID());
		purchaseOrder.setIsSOTrx(false);
		orderBL.setDocTypeTargetId(purchaseOrder);
		//
		purchaseOrder.setDescription(salesOrder.getDescription());

		if (salesOrder.getPOReference() == null)
		{
			purchaseOrder.setPOReference(salesOrder.getDocumentNo());
		}
		else
		{
			purchaseOrder.setPOReference(salesOrder.getPOReference());
		}
		purchaseOrder.setPriorityRule(salesOrder.getPriorityRule());
		purchaseOrder.setSalesRep_ID(salesOrder.getSalesRep_ID());
		purchaseOrder.setM_Warehouse_ID(salesOrder.getM_Warehouse_ID());

		// 08812: Make sure the users are correctly set

		orderBL.setBPartner(purchaseOrder, vendor);
		orderBL.setBill_User_ID(purchaseOrder);

		// Drop Ship
		if (p_IsDropShip)
		{
			purchaseOrder.setIsDropShip(p_IsDropShip);

			if (salesOrder.isDropShip() && salesOrder.getDropShip_BPartner_ID() != 0)
			{
				purchaseOrder.setDropShip_BPartner_ID(salesOrder.getDropShip_BPartner_ID());
				purchaseOrder.setDropShip_Location_ID(salesOrder.getDropShip_Location_ID());
				purchaseOrder.setDropShip_User_ID(salesOrder.getDropShip_User_ID());
			}
			else
			{
				purchaseOrder.setDropShip_BPartner_ID(salesOrder.getC_BPartner_ID());
				purchaseOrder.setDropShip_Location_ID(salesOrder.getC_BPartner_Location_ID());
				purchaseOrder.setDropShip_User_ID(salesOrder.getAD_User_ID());
			}

			// get default drop ship warehouse
			final I_AD_OrgInfo orginfo = orgDAO.retrieveOrgInfo(ctx, org.getAD_Org_ID(), trxName);

			if (orginfo.getDropShip_Warehouse_ID() != 0)
			{
				purchaseOrder.setM_Warehouse_ID(orginfo.getDropShip_Warehouse_ID());
			}
			else
			{
				final ILoggable loggable = getLoggable();
				loggable.addLog("@Missing@ @AD_OrgInfo@ @DropShip_Warehouse_ID@");
			}
		}
		// References
		purchaseOrder.setC_Activity_ID(salesOrder.getC_Activity_ID());
		purchaseOrder.setC_Campaign_ID(salesOrder.getC_Campaign_ID());
		purchaseOrder.setC_Project_ID(salesOrder.getC_Project_ID());
		purchaseOrder.setUser1_ID(salesOrder.getUser1_ID());
		purchaseOrder.setUser2_ID(salesOrder.getUser2_ID());
		purchaseOrder.setC_Currency_ID(salesOrder.getC_Currency_ID());
		//

		InterfaceWrapperHelper.save(purchaseOrder);
		return purchaseOrder;
	}	// createPOForVendor

	private ILoggable getLoggable()
	{
		return ILoggable.THREADLOCAL.getLoggable();
	}
}