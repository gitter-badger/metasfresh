package de.metas.procurement.base.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.bpartner.service.IBPartnerDAO;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Check;
import org.adempiere.util.Services;
import org.adempiere.util.time.SystemTime;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_M_Product;
import org.compiere.util.Env;
import org.slf4j.Logger;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import de.metas.i18n.IModelTranslation;
import de.metas.i18n.IModelTranslationMap;
import de.metas.logging.LogManager;
import de.metas.procurement.base.IPMMBPartnerDAO;
import de.metas.procurement.base.IPMMContractsDAO;
import de.metas.procurement.base.IPMMMessageDAO;
import de.metas.procurement.base.IPMMProductDAO;
import de.metas.procurement.base.model.I_AD_User;
import de.metas.procurement.base.model.I_C_Flatrate_Term;
import de.metas.procurement.base.model.I_PMM_Product;
import de.metas.procurement.sync.protocol.SyncBPartner;
import de.metas.procurement.sync.protocol.SyncContract;
import de.metas.procurement.sync.protocol.SyncContractLine;
import de.metas.procurement.sync.protocol.SyncProduct;
import de.metas.procurement.sync.protocol.SyncUser;

/*
 * #%L
 * de.metas.procurement.base
 * %%
 * Copyright (C) 2016 metas GmbH
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
 * Factory used to create all sync objects that we are sending from metasfresh server to webui server.
 *
 * @author metas-dev <dev@metas-fresh.com>
 *
 */
public class SyncObjectsFactory
{
	public static final SyncObjectsFactory newFactory(final Date date)
	{
		return new SyncObjectsFactory(date);
	}

	public static final SyncObjectsFactory newFactory()
	{
		return new SyncObjectsFactory(SystemTime.asDayTimestamp());
	}

	//
	// services
	private static final Logger logger = LogManager.getLogger(SyncObjectsFactory.class);

	private final transient IBPartnerDAO bpartnerDAO = Services.get(IBPartnerDAO.class);
	private final transient IPMMContractsDAO pmmContractsDAO = Services.get(IPMMContractsDAO.class);
	private final transient IPMMProductDAO pmmProductDAO = Services.get(IPMMProductDAO.class);
	private final transient IPMMBPartnerDAO pmmbPartnerDAO = Services.get(IPMMBPartnerDAO.class);

	//
	// parameters
	private final Date date;

	//
	// state/cache

	private final Map<Integer, I_C_BPartner> bpartners = new HashMap<>();

	/** C_BPartner_ID to {@link I_C_Flatrate_Term}s */
	private final Multimap<Integer, I_C_Flatrate_Term> _bpartnerId2contract = MultimapBuilder.hashKeys().arrayListValues().build();
	private boolean _bpartnerId2contract_fullyLoaded = false;

	private SyncObjectsFactory(final Date date)
	{
		super();

		Check.assumeNotNull(date, "date not null");
		this.date = (Date)date.clone();
	}

	private Properties getCtx()
	{
		return Env.getCtx();
	}

	public List<SyncBPartner> createAllSyncBPartners()
	{
		final List<SyncBPartner> syncBPartners = new ArrayList<>();
		for (final int bpartnerId : getAllBPartnerIds())
		{
			final SyncBPartner syncBPartner = createSyncBPartner(bpartnerId);
			syncBPartners.add(syncBPartner);
		}

		return syncBPartners;
	}

	private SyncContract createSyncContract(final I_C_Flatrate_Term term)
	{
		final SyncContract syncContract = new SyncContract();
		syncContract.setUuid(SyncUUIDs.toUUIDString(term));
		syncContract.setDateFrom(term.getStartDate());
		syncContract.setDateTo(term.getEndDate());

		//
		// Contract Line: 1 line for our PMM_Product
		{
			final I_PMM_Product pmmProduct = term.getPMM_Product();
			if (pmmProduct == null)
			{
				logger.warn("Contract {} has no PMM_Product. Skip exporting.", term);
				return null;
			}

			final SyncProduct syncProduct = createSyncProduct(pmmProduct);
			if (syncProduct == null)
			{
				return null;
			}

			final SyncContractLine syncContractLine = new SyncContractLine();
			syncContractLine.setUuid(syncContract.getUuid());
			syncContractLine.setProduct(syncProduct);

			syncContract.getContractLines().add(syncContractLine);
		}

		return syncContract;
	}

	public SyncBPartner createSyncBPartner(final int bpartnerId)
	{
		//
		// Create SyncBPartner with Users populated
		final SyncBPartner syncBPartner = createSyncBPartnerWithoutContracts(bpartnerId);

		//
		// Populate contracts
		syncBPartner.setSyncContracts(true);
		for (final I_C_Flatrate_Term term : getC_Flatrate_Terms_ForBPartnerId(bpartnerId))
		{
			final SyncContract syncContract = createSyncContract(term);
			if (syncContract == null)
			{
				continue;
			}
			syncBPartner.getContracts().add(syncContract);
		}

		return syncBPartner;
	}

	public SyncBPartner createSyncBPartnerWithoutContracts(final I_C_BPartner bpartner)
	{
		Check.assumeNotNull(bpartner, "bpartner not null");
		return createSyncBPartnerWithoutContracts(bpartner.getC_BPartner_ID());
	}

	private SyncBPartner createSyncBPartnerWithoutContracts(final int bpartnerId)
	{
		final I_C_BPartner bpartner = getC_BPartnerById(bpartnerId);

		final SyncBPartner syncBPartner = new SyncBPartner();
		syncBPartner.setName(bpartner.getName());
		syncBPartner.setUuid(SyncUUIDs.toUUIDString(bpartner));

		// Contracts: we are not populating them here, so, for now we flag them as "do not sync"
		syncBPartner.setSyncContracts(false);

		// not a vendor: no need to look at the contacts. delete the bpartner.
		if(!bpartner.isVendor())
		{
			syncBPartner.setDeleted(true);
			return syncBPartner;
		}

		final String adLanguage = bpartner.getAD_Language();

		//
		// Fill Users
		final List<I_AD_User> contacts = InterfaceWrapperHelper.createList(bpartnerDAO.retrieveContacts(bpartner), I_AD_User.class);

		for (final I_AD_User contact : contacts)
		{
			final SyncUser syncUser = createSyncUser(contact, adLanguage);
			if (syncUser == null)
			{
				continue;
			}
			syncBPartner.getUsers().add(syncUser);
		}

		// no users: also delete the BPartner
		if(syncBPartner.getUsers().isEmpty())
		{
			syncBPartner.setDeleted(true);
		}

		return syncBPartner;
	}

	private SyncUser createSyncUser(final I_AD_User contact, final String adLanguage)
	{
		if (!contact.isActive() || !contact.isIsMFProcurementUser())
		{
			return null;
		}

		final String email = contact.getEMail();
		final String password = contact.getPassword();

		if (Check.isEmpty(email, true))
		{
			return null;
		}

		final SyncUser syncUser = new SyncUser();
		syncUser.setLanguage(adLanguage);
		syncUser.setUuid(SyncUUIDs.toUUIDString(contact));
		syncUser.setEmail(email);
		syncUser.setPassword(password);

		return syncUser;
	}

	private Multimap<Integer, I_C_Flatrate_Term> getC_Flatrate_Terms_IndexedByBPartnerId(final boolean fullyLoadedRequired)
	{
		if (fullyLoadedRequired && !_bpartnerId2contract_fullyLoaded)
		{
			_bpartnerId2contract.clear(); // clear all first

			final List<I_C_Flatrate_Term> terms = pmmContractsDAO.retrieveAllRunningContractsOnDate(date);
			for (final I_C_Flatrate_Term term : terms)
			{
				final int bpartnerId = term.getDropShip_BPartner_ID();
				_bpartnerId2contract.put(bpartnerId, term);
			}

			_bpartnerId2contract_fullyLoaded = true;
		}
		return _bpartnerId2contract;
	}

	private Set<Integer> getAllBPartnerIds()
	{
		final List<I_C_BPartner> bpartnersList = pmmbPartnerDAO.retrieveAllPartnersWithProcurementUsers();
		for (final I_C_BPartner bpartner : bpartnersList)
		{
			bpartners.put(bpartner.getC_BPartner_ID(), bpartner);
		}
		return ImmutableSet.copyOf(bpartners.keySet());
	}

	private I_C_BPartner getC_BPartnerById(final int bpartnerId)
	{
		I_C_BPartner bpartner = bpartners.get(bpartnerId);
		if (bpartner == null)
		{
			bpartner = InterfaceWrapperHelper.create(getCtx(), bpartnerId, I_C_BPartner.class, ITrx.TRXNAME_ThreadInherited);
			bpartners.put(bpartnerId, bpartner);
		}
		return bpartner;
	}

	private Collection<I_C_Flatrate_Term> getC_Flatrate_Terms_ForBPartnerId(final int bpartnerId)
	{
		final boolean fullyLoadedRequired = false;
		final Multimap<Integer, I_C_Flatrate_Term> bpartnerId2contract = getC_Flatrate_Terms_IndexedByBPartnerId(fullyLoadedRequired);
		if (!bpartnerId2contract.containsKey(bpartnerId))
		{
			final List<I_C_Flatrate_Term> contracts = pmmContractsDAO.retrieveRunningContractsOnDateForBPartner(date, bpartnerId);
			bpartnerId2contract.putAll(bpartnerId, contracts);
		}
		return bpartnerId2contract.get(bpartnerId);
	}

	public SyncProduct createSyncProduct(final I_PMM_Product pmmProduct)
	{
		final I_M_Product product = pmmProduct.getM_Product();

		String productName = pmmProduct.getProductName();
		// Fallback to M_Product.Name (shall not happen)
		if (Check.isEmpty(productName, true))
		{
			productName = product == null ? null : product.getName();
		}

		final SyncProduct syncProduct = new SyncProduct();

		final boolean valid = pmmProduct.isActive()
				&& pmmProduct.getM_Warehouse_ID() > 0
				&& pmmProduct.getM_Product_ID() > 0
				&& pmmProduct.getM_HU_PI_Item_Product_ID() > 0;

		syncProduct.setUuid(SyncUUIDs.toUUIDString(pmmProduct));
		syncProduct.setName(productName);
		syncProduct.setPackingInfo(pmmProduct.getPackDescription());

		syncProduct.setShared(pmmProduct.getC_BPartner_ID() <= 0); // share, unless it is assigned to a particular BPartner
		syncProduct.setDeleted(!valid);

		//
		// Translations
		{
			final Map<String, String> syncProductNamesTrl = syncProduct.getNamesTrl();
			final IModelTranslationMap productTrls = InterfaceWrapperHelper.getModelTranslationMap(product);
			final PMMProductNameBuilder productNameTrlBuilder = PMMProductNameBuilder.newBuilder()
					.setPMM_Product(pmmProduct);
			for (final IModelTranslation productLanguageTrl : productTrls.getAllTranslations().values())
			{
				final String adLanguage = productLanguageTrl.getAD_Language();

				final String productNamePartTrl = productLanguageTrl.getTranslation(I_M_Product.COLUMNNAME_Name);
				if (Check.isEmpty(productNamePartTrl, true))
				{
					continue;
				}

				final String productNameTrl = productNameTrlBuilder
						.setProductNamePartIfUsingMProduct(productNamePartTrl)
						.build();
				if (Check.isEmpty(productNameTrl, true))
				{
					continue;
				}

				syncProductNamesTrl.put(adLanguage, productNameTrl.trim());
			}
		}

		return syncProduct;
	}

	public List<SyncProduct> createAllSyncProducts()
	{
		final List<I_PMM_Product> allPmmProducts = pmmProductDAO.retrieveAllPMMProductsValidOnDateQuery(date)
				.addEqualsFilter(I_PMM_Product.COLUMNNAME_C_BPartner_ID, null) // Not bound to a particular partner (i.e. C_BPartner_ID is null)
				//
				.orderBy()
				.addColumn(I_PMM_Product.COLUMN_PMM_Product_ID) // have a predictable order
				.endOrderBy()
				//
				.create()
				.list();

		final List<SyncProduct> syncProducts = new ArrayList<SyncProduct>(allPmmProducts.size());
		for (final I_PMM_Product pmmProduct : allPmmProducts)
		{
			final SyncProduct syncProduct = createSyncProduct(pmmProduct);
			if (syncProduct == null)
			{
				continue;
			}

			syncProducts.add(syncProduct);
		}
		return syncProducts;
	}

	public String createSyncInfoMessage()
	{
		return Services.get(IPMMMessageDAO.class).retrieveMessagesAsString(getCtx());
	}
}
