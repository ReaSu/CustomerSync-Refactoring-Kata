package codingdojo;

import java.util.List;

public class CustomerSync {

    private CustomerDataLayer customerDataLayer;

    public CustomerSync(CustomerDataLayer customerDataLayer) {
        this.customerDataLayer = customerDataLayer;
    }

    public boolean syncWithDataLayer(ExternalCustomer externalCustomer) {

        CustomerMatches customerMatches;
        if (externalCustomer.isCompany()) {
            customerMatches = loadCompany(externalCustomer);
        } else {
            customerMatches = loadPerson(externalCustomer);
        }
        Customer customer = customerMatches.getCustomer();

        if (customer == null) {
            customer = new Customer();
            customer.setExternalId(externalCustomer.getExternalId());
            customer.setMasterExternalId(externalCustomer.getExternalId());
        }
        customer.setAddress(externalCustomer.getPostalAddress());
        customer.setPreferredStore(externalCustomer.getPreferredStore());
        customer.setName(externalCustomer.getName());

        if (externalCustomer.isCompany()) {
            customer.setCompanyNumber(externalCustomer.getCompanyNumber());
            customer.setCustomerType(CustomerType.COMPANY);
        } else {
            customer.setCustomerType(CustomerType.PERSON);
        }

        if (customerMatches.hasDuplicates()) {
            for (Customer duplicate : customerMatches.getDuplicates()) {
                if (duplicate == null) {
                    duplicate = new Customer();
                    duplicate.setExternalId(externalCustomer.getExternalId());
                    duplicate.setMasterExternalId(externalCustomer.getExternalId());
                }

                duplicate.setName(externalCustomer.getName());

                if (duplicate.getInternalId() == null) {
                    this.customerDataLayer.createCustomerRecord(duplicate);
                } else {
                    this.customerDataLayer.updateCustomerRecord(duplicate);
                }
            }
        }

        boolean created = false;
        if (customer.getInternalId() == null) {
            this.customerDataLayer.createCustomerRecord(customer);
            created = true;
        } else {
            this.customerDataLayer.updateCustomerRecord(customer);
        }
        List<ShoppingList> consumerShoppingLists = externalCustomer.getShoppingLists();
        for (ShoppingList consumerShoppingList : consumerShoppingLists) {
            customer.addShoppingList(consumerShoppingList);
            this.customerDataLayer.updateShoppingList(consumerShoppingList);
            this.customerDataLayer.updateCustomerRecord(customer);
        }

        return created;
    }

    public CustomerMatches loadCompany(ExternalCustomer externalCustomer) {

        final String externalId = externalCustomer.getExternalId();
        final String companyNumber = externalCustomer.getCompanyNumber();

        CustomerMatches matches = new CustomerMatches();
        Customer matchByExternalId = customerDataLayer.findByExternalId(externalId);
        if (matchByExternalId != null) {
            matches.setCustomer(matchByExternalId);
            matches.setMatchTerm("ExternalId");
            Customer matchByMasterId = customerDataLayer.findByMasterExternalId(externalId);
            if (matchByMasterId != null) matches.addDuplicate(matchByMasterId);
        } else {
            Customer matchByCompanyNumber = customerDataLayer.findByCompanyNumber(companyNumber);
            if (matchByCompanyNumber != null) {
                matches.setCustomer(matchByCompanyNumber);
                matches.setMatchTerm("CompanyNumber");
            }
        }

        CustomerMatches customerMatches = matches;

        if (customerMatches.getCustomer() != null && !CustomerType.COMPANY.equals(customerMatches.getCustomer().getCustomerType())) {
            throw new ConflictException("Existing customer for externalCustomer " + externalId + " already exists and is not a company");
        }

        if ("ExternalId".equals(customerMatches.getMatchTerm())) {
            String customerCompanyNumber = customerMatches.getCustomer().getCompanyNumber();
            if (!companyNumber.equals(customerCompanyNumber)) {
                customerMatches.getCustomer().setMasterExternalId(null);
                customerMatches.addDuplicate(customerMatches.getCustomer());
                customerMatches.setCustomer(null);
                customerMatches.setMatchTerm(null);
            }
        } else if ("CompanyNumber".equals(customerMatches.getMatchTerm())) {
            String customerExternalId = customerMatches.getCustomer().getExternalId();
            if (customerExternalId != null && !externalId.equals(customerExternalId)) {
                throw new ConflictException("Existing customer for externalCustomer " + companyNumber + " doesn't match external id " + externalId + " instead found " + customerExternalId );
            }
            Customer customer = customerMatches.getCustomer();
            customer.setExternalId(externalId);
            customer.setMasterExternalId(externalId);
            customerMatches.addDuplicate(null);
        }

        return customerMatches;
    }

    public CustomerMatches loadPerson(ExternalCustomer externalCustomer) {
        final String externalId = externalCustomer.getExternalId();

        CustomerMatches matches = new CustomerMatches();
        Customer matchByPersonalNumber = customerDataLayer.findByExternalId(externalId);
        matches.setCustomer(matchByPersonalNumber);
        if (matchByPersonalNumber != null) matches.setMatchTerm("ExternalId");
        CustomerMatches customerMatches = matches;

        if (customerMatches.getCustomer() != null) {
            if (!CustomerType.PERSON.equals(customerMatches.getCustomer().getCustomerType())) {
                throw new ConflictException("Existing customer for externalCustomer " + externalId + " already exists and is not a person");
            }

            if (!"ExternalId".equals(customerMatches.getMatchTerm())) {
                Customer customer = customerMatches.getCustomer();
                customer.setExternalId(externalId);
                customer.setMasterExternalId(externalId);
            }
        }

        return customerMatches;
    }
}
