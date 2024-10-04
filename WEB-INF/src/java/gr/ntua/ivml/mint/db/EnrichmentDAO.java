package gr.ntua.ivml.mint.db;

import java.util.List;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import gr.ntua.ivml.mint.persistent.Enrichment;
import gr.ntua.ivml.mint.persistent.Organization;

public class EnrichmentDAO extends DAO<Enrichment, Long> {

    public List<Enrichment> findByOrganization(Organization org ) {
        if( org != null )
            return getSession().createQuery("from Enrichment where organization=:org order by organization")
                    .setEntity("org", org)
                    .list();
        else
            return getSession().createQuery("from Enrichment where organization is null order by organization")
                    .list();
    }

    public List<Enrichment> pageByOrganization( Organization org, int start, int count  ) {
    	return pageByCriteria( start, count, Order.desc("creationDate") ,Restrictions.eq("organization", org));
    }
}
