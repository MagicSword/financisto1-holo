/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package tw.tib.financisto.model;

import android.database.Cursor;
import tw.tib.financisto.db.DatabaseHelper.SmsTemplateColumns;
import tw.tib.financisto.db.DatabaseHelper.SmsTemplateListColumns;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

import static tw.tib.financisto.db.DatabaseHelper.SMS_TEMPLATES_TABLE;
import static tw.tib.orb.EntityManager.DEF_SORT_COL;

@Entity
@Table(name = SMS_TEMPLATES_TABLE)
public class SmsTemplate extends MyEntity implements SortableEntity {

    @Column(name = "template")
    public String template;

    @Column(name = "note")
    public String note;

    @Column(name = "category_id")
    public long categoryId;

    @Column(name = "payee_id")
    public long payeeId;

    @Column(name = "project_id")
    public long projectId;

    @Column(name = "account_id")
    public long accountId = -1;

    @Column(name = "to_account_id")
    public long toAccountId = -1;

    @Column(name = "is_income")
    public boolean isIncome;

    @Column(name = DEF_SORT_COL)
    public long sortOrder;

    @Transient
    public String categoryName;

    @Transient
    public int categoryLevel;

    @Transient
    public String payeeName;

    @Transient
    public String projectName;

    public static SmsTemplate fromCursor(Cursor c) {
        SmsTemplate t = new SmsTemplate();
        t.id = c.getLong(SmsTemplateColumns._id.ordinal());
        t.title = c.getString(SmsTemplateColumns.title.ordinal());
        t.template = c.getString(SmsTemplateColumns.template.ordinal());
        t.note = c.getString(SmsTemplateColumns.note.ordinal());
        t.categoryId = c.getLong(SmsTemplateColumns.category_id.ordinal());
        t.payeeId = c.getLong(SmsTemplateColumns.payee_id.ordinal());
        t.projectId = c.getLong(SmsTemplateColumns.project_id.ordinal());
        t.accountId = c.getLong(SmsTemplateColumns.account_id.ordinal());
        t.toAccountId = c.getLong(SmsTemplateColumns.to_account_id.ordinal());
        t.isIncome = c.getInt(SmsTemplateColumns.is_income.ordinal()) != 0;
        t.sortOrder = c.getLong(SmsTemplateColumns.sort_order.ordinal());
        return t;
    }

    public static SmsTemplate fromListCursor(Cursor c) {
        SmsTemplate t = fromCursor(c);
        int offset = SmsTemplateColumns.values().length;
        t.categoryName = c.getString(offset + SmsTemplateListColumns.cat_name.ordinal());
        t.categoryLevel = c.getInt(offset + SmsTemplateListColumns.cat_level.ordinal());
        t.payeeName = c.getString(offset + SmsTemplateListColumns.payee_name.ordinal());
        t.projectName = c.getString(offset + SmsTemplateListColumns.project_name.ordinal());
        return t;
    }

    @Override
    public long getSortOrder() {
        return sortOrder;
    }
}
