package org.eclipse.ceylon.model.typechecker.model;

import static org.eclipse.ceylon.model.typechecker.model.ModelUtil.formatPath;
import static org.eclipse.ceylon.model.typechecker.model.ModelUtil.isNameMatching;
import static org.eclipse.ceylon.model.typechecker.model.ModelUtil.isOverloadedVersion;
import static org.eclipse.ceylon.model.typechecker.model.ModelUtil.isResolvable;
import static org.eclipse.ceylon.model.typechecker.model.ModelUtil.lookupMember;
import static org.eclipse.ceylon.model.typechecker.model.ModelUtil.lookupMemberForBackend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.ceylon.common.Backends;

public class Package 
        implements ImportableScope, Referenceable, Annotated {

    private List<String> name;
    private Module module;
    private List<Unit> units = 
            new ArrayList<Unit>();
    private boolean shared = false;
    private List<String> restrictions;
    private List<Annotation> annotations = 
            new ArrayList<Annotation>();
    private Unit unit;
    private String nameAsString;
    
    @Override
    public boolean isToplevel() {
        return false;
    }
    
    public Module getModule() {
        return module;
    }

    public void setModule(Module module) {
        this.module = module;
    }

    public List<String> getName() {
        return name;
    }

    public void setName(List<String> name) {
        this.name = name;
    }
    
    public Iterable<Unit> getUnits() {
        synchronized (units) {
            return new ArrayList<Unit>(units);
        }
    }
    
    public void addUnit(Unit unit) {
        synchronized (units) {
            units.add(unit);
            members=null;
        }
    }
    
    public void removeUnit(Unit unit) {
        synchronized (units) {
            units.remove(unit);
            members=null;
        }
    }
    
    public boolean isShared() {
        return shared;
    }
    
    public void setShared(boolean shared) {
        this.shared = shared;
    }
    
    public List<String> getRestrictions() {
        return restrictions;
    }
    
    public void setRestrictions(List<String> restrictions) {
        this.restrictions = restrictions;
    }

    public boolean withinRestrictions(Unit unit) {
        return withinRestrictions(unit.getPackage().getModule());
    }
    
    private boolean withinRestrictions(Module module) {
        List<String> restrictions = getRestrictions();
        if (restrictions==null) {
            return true;
        }
        else {
            String name = module.getNameAsString();
            for (String mod: restrictions) {
                if (name.equals(mod)) {
                    return true;
                }
            }
            return false;
        }
    }

    private List<Declaration> members;
    
    @Override
    public List<Declaration> getMembers() {
        synchronized (units) {
            //return getMembersInternal();
            if (members==null) {
                members = getMembersInternal();
            }
            return members;
        }
    }
    
    @Override
    public void addMember(Declaration declaration) {
        members=null;
    }
    
    private List<Declaration> getMembersInternal() {
        List<Declaration> result = 
                new ArrayList<Declaration>();
        for (Unit unit: units) {
            for (Declaration d: unit.getDeclarations()) {
                if (d.getContainer().equals(this)) {
                    result.add(d);
                }
            }
        }
        return result;
    }

    @Override
    public Scope getContainer() {
        return null;
    }

    @Override
    public Scope getScope() {
        return null;
    }

    public String getNameAsString() {
        if (nameAsString == null) {
            nameAsString = formatPath(name);
        }
        return nameAsString;
    }
    
    public boolean isLanguagePackage() {
        List<String> name = getName();
        return name.size()==2
                && name.get(0)
                    .equals("ceylon")
                && name.get(1)
                    .equals("language");
    }
    
    public boolean isDefaultPackage() {
        return getName().isEmpty();
    }

    @Override
    public String toString() {
        return "package " + getNameAsString();
    }
    
    @Override
    public String getQualifiedNameString() {
        return getNameAsString();
    }
    
    /**
     * Search only inside the package, ignoring imports
     */
    @Override
    public Declaration getMember(String name, 
            List<Type> signature, boolean variadic) {
        if (signature==null 
                && name.equals("Nothing")
                && isLanguagePackage()) {
            return getUnit().getNothingDeclaration();
        }
        return getDirectMember(name, signature, variadic);
    }

    @Override
    public Declaration getDirectMember(String name, 
            List<Type> signature, boolean variadic) {
        return lookupMember(getMembers(), 
                name, signature, variadic);
    }

    @Override
    public Declaration getDirectMemberForBackend(String name, 
            Backends backends) {
        return lookupMemberForBackend(getMembers(), 
                name, backends);
    }

    @Override
    public Type getDeclaringType(Declaration d) {
        if (d.isClassMember()) {
            Class containingAnonClass =
                    (Class) d.getContainer();
            if (containingAnonClass.isAnonymous()) {
                return containingAnonClass.getType();
            }
        }
        return null;
    }

    /**
     * Search in the package, taking into account
     * imports
     */
    @Override
    public Declaration getMemberOrParameter(Unit unit, 
            String name, List<Type> signature, 
            boolean variadic) {
        //this implements the rule that imports hide 
        //toplevel members of a package
        //TODO: would it be better to look in the given unit 
        //      first, before checking imports?
        Declaration d = 
                unit.getImportedDeclaration(name, 
                        signature, variadic);
        if (d!=null) {
            return d;
        }
        d = getDirectMember(name, signature, variadic);
        if (d!=null) {
            return d;
        }
        return unit.getLanguageModuleDeclaration(name);
    }
    
    @Override
    public boolean isInherited(Declaration d) {
        return false;
    }
    
    @Override
    public TypeDeclaration getInheritingDeclaration(Declaration d) {
        return null;
    }
    
    @Override
    public Map<String,DeclarationWithProximity> 
    getMatchingDeclarations(Unit unit, String startingWith, 
            int proximity, Cancellable canceller) {
        Map<String,DeclarationWithProximity> result = 
                getMatchingDirectDeclarations(startingWith, 
                        //package toplevels - just less than 
                        //explicitly imported declarations
                        proximity+1, canceller);
        if (unit!=null) {
            result.putAll(unit.getMatchingImportedDeclarations(
                    startingWith, proximity, canceller));
        }
        Map<String,DeclarationWithProximity> importables = 
                getModule()
                    .getAvailableDeclarations(unit,
                            startingWith, proximity, canceller);
        List<Map.Entry<String,DeclarationWithProximity>> 
        entriesToAdd =
                new ArrayList
                <Map.Entry<String,DeclarationWithProximity>>
                        (importables.size());
        for (Map.Entry<String,DeclarationWithProximity> e: 
                importables.entrySet()) {
            boolean already = false;
            DeclarationWithProximity existing = e.getValue();
            for (DeclarationWithProximity importable: 
                    result.values()) {
                Declaration id = importable.getDeclaration();
                Declaration ed = existing.getDeclaration();
                if (id.equals(ed)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                entriesToAdd.add(e);
            }
        }
        for (Map.Entry<String,DeclarationWithProximity> e:
                entriesToAdd) {
            result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    public Map<String,DeclarationWithProximity> 
    getMatchingDirectDeclarations(String startingWith, 
            int proximity, Cancellable canceller) {
        Map<String,DeclarationWithProximity> result = 
                new TreeMap<String,DeclarationWithProximity>();
        for (Declaration d: getMembers()) {
            if (canceller != null
                    && canceller.isCancelled()) {
                return Collections.emptyMap();
            }
            if (isResolvable(d) && 
                    !isOverloadedVersion(d) && 
                    isNameMatching(startingWith, d)) {
                result.put(d.getName(unit), 
                        new DeclarationWithProximity(d, 
                                proximity));
            }
        }
        return result;
    }

    public Map<String,DeclarationWithProximity> 
    getImportableDeclarations(Unit unit, String startingWith, 
            List<Import> imports, int proximity, Cancellable canceller) {
        Map<String,DeclarationWithProximity> result = 
                new TreeMap<String,DeclarationWithProximity>();
        for (Declaration d: getMembers()) {
            if (canceller != null
                    && canceller.isCancelled()) {
                return Collections.emptyMap();
            }
            if (isResolvable(d) && d.isShared() && 
                    !isOverloadedVersion(d) &&
                    isNameMatching(startingWith, d)) {
                boolean already = false;
                for (Import i: imports) {
                    if (!i.isWildcardImport() && 
                            i.getDeclaration().equals(d)) {
                        already = true;
                        break;
                    }
                }
                if (!already) {
                    result.put(d.getName(), 
                            new DeclarationWithProximity(d, 
                                    proximity));
                }
            }
        }
        return result;
    }
    
    @Override
    public List<Annotation> getAnnotations() {
        return annotations;
    }

    @Override
    public int hashCode() {
        // use the cached version, profiling says 
        // List.hashCode is expensive
        return getNameAsString().hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Package) {
            Package p = (Package) obj;
            return p.getNameAsString()
                    .equals(getNameAsString());
        }
        else {
            return false;
        }
    }
    
    @Override
    public Unit getUnit() {
        return unit;
    }
    
    public void setUnit(Unit unit) {
        this.unit = unit;
    }
    
    @Override
    public Backends getScopedBackends() {
        return getModule().getNativeBackends();
    }
}
