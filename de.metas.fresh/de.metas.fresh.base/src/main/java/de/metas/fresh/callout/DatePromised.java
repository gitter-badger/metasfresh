package de.metas.fresh.callout;

/*
 * #%L
 * de.metas.fresh.base
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


import java.sql.Timestamp;
import java.util.Calendar;
import org.slf4j.Logger;
import de.metas.logging.LogManager;

import org.adempiere.ad.ui.spi.TabCalloutAdapter;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.Check;
import org.compiere.model.GridTab;
import org.compiere.model.I_C_Order;
import org.compiere.util.TimeUtil;

/**
 * This callout is supposed to be usable for <b>any</b> tab with a DatePromised field.
 * 
 */
// task 06706
public class DatePromised extends TabCalloutAdapter
{
	private static final Logger logger = LogManager.getLogger(DatePromised.class);

	private static final String FIELDNAME_DATEPROMISED = I_C_Order.COLUMNNAME_DatePromised;

	@Override
	public void onNew(final GridTab gridTab)
	{
		Check.assumeNotNull(gridTab, "Param 'gridTab' is not null");

		final Timestamp datePromised = (Timestamp)gridTab.getValue(FIELDNAME_DATEPROMISED);
		if (datePromised == null)
		{
			// issuing a warning
			final String msg = "GridTab " + gridTab + " has no value for column name " + FIELDNAME_DATEPROMISED + ". "
					+ "Please make sure the field exists and the column has a default value, or remove the tab callout";
			logger.warn(msg, new AdempiereException(msg));

			return; // nothing we can do
		}

		final Calendar datePromisedCal = TimeUtil.asCalendar(datePromised);

		//
		// If time is already set return
		if (datePromisedCal.get(Calendar.HOUR_OF_DAY) > 0
				|| datePromisedCal.get(Calendar.MINUTE) > 0)
		{
			return;
		}

		datePromisedCal.set(Calendar.HOUR_OF_DAY, 23);
		datePromisedCal.set(Calendar.MINUTE, 59);
		datePromisedCal.set(Calendar.SECOND, 59);
		datePromisedCal.set(Calendar.MILLISECOND, 999);

		final Timestamp datePromisedNew = new Timestamp(datePromisedCal.getTimeInMillis());
		gridTab.setValue(FIELDNAME_DATEPROMISED, datePromisedNew);
	}
}
