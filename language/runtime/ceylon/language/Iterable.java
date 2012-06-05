package ceylon.language;

import com.redhat.ceylon.compiler.java.metadata.Name;
import com.redhat.ceylon.compiler.java.metadata.Ceylon;
import com.redhat.ceylon.compiler.java.metadata.SatisfiedTypes;
import com.redhat.ceylon.compiler.java.metadata.TypeInfo;
import com.redhat.ceylon.compiler.java.metadata.TypeParameter;
import com.redhat.ceylon.compiler.java.metadata.TypeParameters;
import com.redhat.ceylon.compiler.java.metadata.Variance;

@Ceylon
@TypeParameters(@TypeParameter(value = "Element", variance = Variance.OUT))
@SatisfiedTypes("ceylon.language.Container")
public interface Iterable<Element> extends Container {

    public boolean getEmpty();
    
    @TypeInfo("ceylon.language.Iterator<Element>")
    public Iterator<? extends Element> getIterator();

    @TypeInfo("ceylon.language.Empty|ceylon.language.Sequence<Element>")
    public Iterable<? extends Element> getSequence();

    @TypeInfo("ceylon.language.Iterable<Result>")
    public <Result> Iterable<? extends Result> map(@Name("collecting")
        @TypeInfo("ceylon.language.Callable<Result,Element>")
        Callable<? extends Result> collecting);

    @TypeInfo("ceylon.language.Iterable<Element>")
    public Iterable<? extends Element> filter(@Name("selecting")
        @TypeInfo("ceylon.language.Callable<ceylon.language.Boolean,Element>")
        Callable<? extends Boolean> selecting);

    @TypeInfo("Result")
    public <Result> Result fold(@Name("initial")
		@TypeInfo("Result") Result initial,
		@Name("accumulating")
        @TypeInfo("ceylon.language.Callable<Result,Result,Element>")
        Callable<? extends Result> accumulating);

    @TypeInfo("Element|ceylon.language.Nothing")
    public Element find(@Name("selecting")
        @TypeInfo("ceylon.language.Callable<ceylon.language.Boolean,Element>")
        Callable<? extends Boolean> selecting);

}
