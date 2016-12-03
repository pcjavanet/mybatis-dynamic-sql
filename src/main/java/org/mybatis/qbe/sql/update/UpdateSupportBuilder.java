/**
 *    Copyright 2016 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.qbe.sql.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.mybatis.qbe.Condition;
import org.mybatis.qbe.sql.SqlCriterion;
import org.mybatis.qbe.sql.SqlField;
import org.mybatis.qbe.sql.where.AbstractWhereBuilder;

public interface UpdateSupportBuilder {

    static SetBuilder updateSupport() {
        return new SetBuilder();
    }
    
    static class SetBuilder {
        private List<FieldAndValue<?>> fieldsAndValues = new ArrayList<>();
        
        public SetBuilder() {
            super();
        }
        
        public <T> SetBuilder set(SqlField<T> field, Optional<T> value) {
            value.ifPresent(v -> set(field, v));
            return this;
        }
        
        public <T> SetBuilder set(SqlField<T> field, T value) {
            fieldsAndValues.add(FieldAndValue.of(field, value));
            return this;
        }
        
        public <T> SetBuilder setNull(SqlField<T> field) {
            fieldsAndValues.add(FieldAndValue.of(field));
            return this;
        }
        
        public <T> WhereBuilder where(SqlField<T> field, Condition<T> condition, SqlCriterion<?>...subCriteria) {
            return new WhereBuilder(fieldsAndValues, field, condition, subCriteria);
        }
    }
    
    static class WhereBuilder extends AbstractWhereBuilder<WhereBuilder> {
        private List<FieldAndValue<?>> setFieldsAndValues;
        
        public <T> WhereBuilder(List<FieldAndValue<?>> setFieldsAndValues, SqlField<T> field, Condition<T> condition, SqlCriterion<?>...subCriteria) {
            super(field, condition, subCriteria);
            this.setFieldsAndValues = setFieldsAndValues;
        }
        
        public UpdateSupport build() {
            AtomicInteger sequence = new AtomicInteger(1);
            Map<String, Object> parameters = new HashMap<>();
            String setClause = renderSetValues(sequence, parameters);
            String whereClause = renderCriteria(SqlField::nameIgnoringTableAlias, sequence, parameters);
            return UpdateSupport.of(setClause, whereClause, parameters);
        }
        
        private String renderSetValues(AtomicInteger sequence, Map<String, Object> parameters) {
            List<String> phrases = new ArrayList<>();
            
            setFieldsAndValues.forEach(fv -> {
                int number = sequence.getAndIncrement();
                SqlField<?> field = fv.field;
                String phrase = String.format("%s = %s", field.nameIgnoringTableAlias(), //$NON-NLS-1$
                        field.getParameterRenderer(String.format("parameters.p%s",  number)).render());
                phrases.add(phrase);
                parameters.put(String.format("p%s", number), fv.value); //$NON-NLS-1$
            });
            
            return phrases.stream().collect(Collectors.joining(", ", "set ", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }

        @Override
        public WhereBuilder getThis() {
            return this;
        }
    }
    
    /**
     * A little pair that holds the field and value.  Only intended for use by the builders.
     * 
     * @param <T> the field type
     */
    static class FieldAndValue<T> {
        private T value;
        private SqlField<T> field;
        
        private static <T> FieldAndValue<T> of(SqlField<T> field, T value) {
            FieldAndValue<T> phrase = new FieldAndValue<>();
            phrase.value = value;
            phrase.field = field;
            return phrase;
        }

        private static <T> FieldAndValue<T> of(SqlField<T> field) {
            FieldAndValue<T> phrase = new FieldAndValue<>();
            phrase.field = field;
            return phrase;
        }
    }
}