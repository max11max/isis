package org.nakedobjects.object.defaults;

import org.nakedobjects.object.LoadedObjects;
import org.nakedobjects.object.NakedObject;
import org.nakedobjects.object.NakedObjectRuntimeException;
import org.nakedobjects.object.ResolveException;
import org.nakedobjects.object.persistence.Oid;
import org.nakedobjects.utility.Assert;
import org.nakedobjects.utility.DebugString;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.log4j.Logger;


public class LoadedObjectsHashtable implements LoadedObjects {
    private static final Logger LOG = Logger.getLogger(LoadedObjectsHashtable.class);
    protected Hashtable loaded = new Hashtable();

    public LoadedObjectsHashtable() {
        LOG.info("create loaded object lookup " + hashCode());
    }

    public NakedObject getLoadedObject(Oid oid) {
        if (oid == null) {
            throw new IllegalArgumentException("OID is null");
        }
        return (NakedObject) loaded.get(oid);
    }

    public boolean isLoaded(Oid oid) {
        if (oid == null) {
            throw new IllegalArgumentException("OID is null");
        }
        return loaded.containsKey(oid);
    }

    public void loaded(NakedObject object) throws ResolveException {
        Oid oid = object.getOid();
        if (oid == null) {
            throw new IllegalArgumentException("OID is null");
        }
        if (isLoaded(oid)) {
            throw new NakedObjectRuntimeException("cannot add as loaded object; oid " + oid + " already present: "
                    + getLoadedObject(oid));
        }
        if (loaded.contains(object)) {
            throw new NakedObjectRuntimeException("cannot add as loaded object; object already present, but with a different oid: " + object  );
        }
        
        LOG.debug("Added loaded object " + object + " as " + oid);
        loaded.put(oid, object);
    }

    public void unloaded(NakedObject object) {
        Assert.assertTrue("cannot unload object as it is not loaded", loaded.contains(object));
        LOG.debug("Removed loaded object " + object);
        loaded.remove(object.getOid());
    }

    public Enumeration dirtyObjects() {
        Enumeration allObjects = loaded.elements();
        Vector v = new Vector();
        while (allObjects.hasMoreElements()) {
            NakedObject object = (NakedObject) allObjects.nextElement();
            if (object.isPersistDirty()) {
                v.addElement(object);
            }
        }
        return v.elements();
    }

    protected void finalize() throws Throwable {
        super.finalize();
        LOG.info("finalizing loaded objects");
    }

    public String getDebugData() {
        DebugString debug = new DebugString();
        Enumeration e = loaded.keys();
        while (e.hasMoreElements()) {
            Oid oid = (Oid) e.nextElement();
            NakedObject object = (NakedObject) loaded.get(oid);
            debug.append(oid.toString());
            debug.append("    ");
            debug.appendln(object.toString());
        }
        return debug.toString();
    }

    public String getDebugTitle() {
        return "LoadedObjectsHashtable";
    }

    public void shutdown() {
        loaded.clear();
        loaded = null;
    }

    public void reset() {
        loaded.clear();
    }
}

/*
 * Naked Objects - a framework that exposes behaviourally complete business
 * objects directly to the user. Copyright (C) 2000 - 2005 Naked Objects Group
 * Ltd
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * The authors can be contacted via www.nakedobjects.org (the registered address
 * of Naked Objects Group is Kingsway House, 123 Goldworth Road, Woking GU21
 * 1NR, UK).
 */