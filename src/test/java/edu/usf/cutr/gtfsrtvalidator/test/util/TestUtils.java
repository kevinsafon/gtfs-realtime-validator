/*
 * Copyright (C) 2017 University of South Florida.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.usf.cutr.gtfsrtvalidator.test.util;

import edu.usf.cutr.gtfsrtvalidator.api.model.ValidationRule;
import edu.usf.cutr.gtfsrtvalidator.helper.ErrorListHelperModel;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Utilities to help with test execution and assertions
 */
public class TestUtils {

    /**
     * Asserts that for a given map of rules to expected number of warnings/errors (expectedErrorsWarnings) and
     * error/warning results (results), there should be a certain number of errors warnings for each rule.  There should
     * be 0 errors/warnings for all other rules not included in the map.  In expectedErrorsWarnings, the key is the
     * ValidationRule, and the value is the number of expected warnings/errors for that rule.
     *
     * @param expectedErrorsWarnings      A map of the ValidationRules, and the number of expected warnings/errors for each rule.  If a ValidationRule isn't included in this map, there should be 0 errors/warnings for that rule
     * @param results                     list of errors or warnings output from validation
     */
    public static void assertResults(Map<ValidationRule, Integer> expectedErrorsWarnings, List<ErrorListHelperModel> results) {
        if (results == null) {
            throw new IllegalArgumentException("results cannot be null - it must be a list of errors or warnings");
        }
        // Check to make sure that the results list isn't empty if we're expecting at least one error or warnings
        int totalExpectedErrorsWarnings = 0;
        for (Integer i : expectedErrorsWarnings.values()) {
            totalExpectedErrorsWarnings = totalExpectedErrorsWarnings + i;
        }
        if (results.isEmpty() && totalExpectedErrorsWarnings > 0) {
            throw new IllegalArgumentException("If at least one error or warning is expected, the results list cannot be empty");
        }
        for (ErrorListHelperModel error : results) {
            Integer i = expectedErrorsWarnings.get(error.getErrorMessage().getValidationRule());
            if (i != null) {
                // Make sure we have i number of errors/warnings
                assertEquals(i.intValue(), error.getOccurrenceList().size());
            } else {
                // Make sure there aren't any errors/warnings for this rule, as it wasn't in the HashMap
                assertEquals(0, error.getOccurrenceList().size());
            }
        }
    }
}
