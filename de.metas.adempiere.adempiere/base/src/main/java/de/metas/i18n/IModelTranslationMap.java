package de.metas.i18n;

import java.util.Map;

import de.metas.i18n.impl.NullModelTranslation;

/*
 * #%L
 * de.metas.adempiere.adempiere.base
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
 * Contains all translated column values for a particular model (record) and for ALL languages.
 * 
 * @author metas-dev <dev@metas-fresh.com>
 *
 */
public interface IModelTranslationMap
{
	/**
	 * @return all {@link IModelTranslation}s for AD_Language; if no translation was found then {@link NullModelTranslation} will be returned
	 */
	IModelTranslation getTranslation(String adLanguage);

	/**
	 * @return all {@link IModelTranslation}s indexed by AD_Language
	 */
	Map<String, IModelTranslation> getAllTranslations();
}
