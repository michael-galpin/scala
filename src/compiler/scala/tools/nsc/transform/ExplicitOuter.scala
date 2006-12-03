/* NSC -- new Scala compiler
 * Copyright 2005-2006 LAMP/EPFL
 * @author Martin Odersky
 */
// $Id$

package scala.tools.nsc.transform

import symtab._
import Flags._
import scala.collection.mutable.{HashMap, ListBuffer}
import matching.{TransMatcher, PatternNodes, CodeFactory, PatternMatchers}

/** This class ...
 *
 *  @author  Martin Odersky
 *  @version 1.0
 */
abstract class ExplicitOuter extends InfoTransform with TransMatcher with PatternNodes with CodeFactory with PatternMatchers with TypingTransformers {
  import global._
  import definitions._
  import posAssigner.atPos

  /** The following flags may be set by this phase: */
  override def phaseNewFlags: long = notPRIVATE | notPROTECTED

  /** the name of the phase: */
  val phaseName: String = "explicitouter"

  /** This class does not change linearization */
  override def changesBaseClasses = false

  protected def newTransformer(unit: CompilationUnit): Transformer =
    new ExplicitOuterTransformer(unit)

  /** Is given <code>clazz</code> an inner class? */
  private def isInner(clazz: Symbol) =
    !clazz.isPackageClass && !clazz.outerClass.isStaticOwner

  private def outerField(clazz: Symbol): Symbol = {
    val result = clazz.info.member(nme.getterToLocal(nme.OUTER))
    if (result == NoSymbol)
      assert(false, "no outer field in "+clazz+clazz.info.decls+" at "+phase)
    result
  }

  def outerAccessor(clazz: Symbol): Symbol = {
    val firstTry = clazz.info.decl(clazz.expandedName(nme.OUTER))
    if (firstTry != NoSymbol && firstTry.outerSource == clazz) firstTry
    else {
      var e = clazz.info.decls.elems
      while ((e ne null) && e.sym.outerSource != clazz) e = e.next
      if (e ne null) e.sym else NoSymbol
    }
  }

// <removeOption>
  /** returns true if tpe was a real option type at prev phase, i.e.
   *   one that  admits to extract an element type.
   * @return true for real option type, false for all others, in particular None, scala.None.type, object scala.None
   */
  def wasOption(tpe: Type): Boolean = ( tpe ne null ) && (tpe match {
    case NoType => 
      false
    case SingleType(_,sym) if sym eq definitions.NoneClass =>
      false

    case ThisType(sym) if sym eq definitions.SomeClass => // also eliminate Option.this.type?
      false

    case SuperType(_,_) => 
      false

    case _ =>
      if(definitions.NoneClass eq tpe.symbol.linkedModuleOfClass) {
	return false
      }
    var res: Option[Boolean] = None
    atPhase(phase.prev) {
      val it = tpe.baseClasses.elements; while(it.hasNext && res.isEmpty) {
	val tp = it.next
	if(tp.linkedModuleOfClass eq definitions.NoneClass) { 
	  res = Some(false) // extremely rare, only appears in refinements, see dbc library
	}
	else if(definitions.isOptionType(tp.tpe)) {
	  res = Some(true)
	}
      }
    }
    return if(res.isEmpty) false else res.get
  })
  
  /** like @see wasOption, additionally tests whether argument is a reference type
   */
  def wasOptionRef(tpe:Type): Boolean = try {
    wasOption(tpe) && isSubType(getOptionArg(tpe), definitions.AnyRefClass.tpe)
  } catch {
    case e => 
      atPhase(phase.prev) {
	e.printStackTrace()
      Console.println("boom"+e.getMessage()+" tpe is "+tpe)
      Console.println("baseclasses are "+tpe.baseClasses)
      }
      System.exit(-1)
    false
  }
  
  /** @precond wasOption(tpe)
   */
  def getOptionArg(tpe:Type): Type = {
    //Console.println("huh?"+wasOption(tpe)+" bah: "+tpe)
    if (tpe.isInstanceOf[SingleType]) 
      getOptionArg(atPhase(phase.prev){tpe.widen}) 
    else if(tpe.symbol != null && tpe.symbol.isRefinementClass) {
      getOptionArg(tpe.baseType(definitions.OptionClass))
    } else {
      atPhase(phase.prev){tpe}.typeArgs(0)
    }
  }

//</removeOption>
  /** <p>
   *    The type transformation method:
   *  </p>
   *  <ol>
   *    <li>
   *      Add an outer parameter to the formal parameters of a constructor
   *      in a inner non-trait class;
   *    </li>
   *    <li>
   *      Add a protected <code>$outer</code> field to an inner class which is
   *      not a trait.
   *    </li>
   *    <li>
   *      <p>
   *        Add an outer accessor <code>$outer$$C</code> to every inner class
   *        with fully qualified name <code>C</code> that is not an interface.
   *        The outer accesssor is abstract for traits, concrete for other
   *        classes.
   *      </p>
   *      <p>
   *        3a. Also add overriding accessor defs to every class that inherits
   *        mixin classes with outer accessor defs (unless the superclass
   *        already inherits the same mixin).
   *      </p>
   *    </li>
   *    <li>
   *      Add a mixin constructor <code>$init$</code> to all mixins except interfaces
   *      Leave all other types unchanged. todo: move to later
   *    </li>
   *    <li>
   *      Make all super accessors and modules in traits non-private, mangling
   *      their names.
   *    </li>
   *    <li>
   *      Remove protected flag from all members of traits.
   *    </li>
   *  </ol>
   */
  def transformInfo(sym: Symbol, tp: Type): Type = tp match {
    case MethodType(formals1, restpe1) =>
      //Console.println("hello methodtype"+tp)
      val formals = if(settings.Xkilloption.value) {formals1 map {x=>transformInfo(sym, x)}}  // removeOption 
                    else formals1
      val restpe = transformInfo(sym, restpe1) // removeOption 
      if (sym.owner.isTrait && ((sym hasFlag SUPERACCESSOR) || sym.isModule)) { // 5 
        sym.makeNotPrivate(sym.owner)
      }
      if (sym.owner.isTrait && (sym hasFlag PROTECTED)) sym setFlag notPROTECTED // 6
      if (sym.isClassConstructor && isInner(sym.owner)) // 1
        MethodType(sym.owner.outerClass.thisType :: formals, restpe)
      else if (restpe ne restpe1)
        MethodType(formals, restpe) // removeOption
      else tp
    case ClassInfoType(parents1, decls, clazz) =>
      if(settings.Xkilloption.value && (clazz.linkedModuleOfClass eq definitions.NoneClass)) 
        return tp
      val parents = if(settings.Xkilloption.value) parents1 map {x => transformInfo(sym,x)} 
                    else parents1
      var decls1 = decls
      if (isInner(clazz) && !(clazz hasFlag INTERFACE)) {
        decls1 = newScope(decls.toList)
        val outerAcc = clazz.newMethod(clazz.pos, nme.OUTER) // 3
        outerAcc.expandName(clazz)
        val restpe = if (clazz.isTrait) clazz.outerClass.tpe else clazz.outerClass.thisType
        decls1 enter clazz.newOuterAccessor(clazz.pos).setInfo(MethodType(List(), restpe))
        if (!clazz.isTrait) // 2
          //todo: avoid outer field if superclass has same outer value?
          decls1 enter (
            clazz.newValue(clazz.pos, nme.getterToLocal(nme.OUTER))
            setFlag (SYNTHETIC | PROTECTED | PARAMACCESSOR)
            setInfo clazz.outerClass.thisType)
      }
      if (!clazz.isTrait && !parents.isEmpty) {
        for (val mc <- clazz.mixinClasses) {
          val mixinOuterAcc: Symbol = atPhase(phase.next)(outerAccessor(mc))
          if (mixinOuterAcc != NoSymbol) {
            if (decls1 eq decls) decls1 = newScope(decls.toList)
            val newAcc = mixinOuterAcc.cloneSymbol(clazz) 
            newAcc.resetFlag(DEFERRED)
            newAcc.setInfo(clazz.thisType.memberType(mixinOuterAcc))
            decls1 enter newAcc 
          }
        }
      }
      if ((decls1 eq decls)&&(parents1 eq parents)) tp else ClassInfoType(parents, decls1, clazz)
    case PolyType(tparams, restp) =>
      val restp1 = transformInfo(sym, restp)
      if (restp eq restp1) tp else PolyType(tparams, restp1)
    // <removeOption>
    case TypeRef(pre,sym,args) if(settings.Xkilloption.value) => 
      if(wasOptionRef(tp)) { // ((sym eq definitions.OptionClass) || (sym eq definitions.SomeClass)) {
        getOptionArg(tp)
      } else {
        val t = typeRef(pre,sym, args.map {t=>transformInfo(sym,t)})
        //Console.println("turning typeref "+tp+" to "+t)
        t
      }
    
    // </removeOption>

    case _ =>
      if(!settings.Xkilloption.value) 
        tp
      else { // bq: gross hack for removing options in refinements (e.g. dbc). YMMV
	  if(tp.symbol.isRefinementClass) {
	    if(settings.debug.value) 
	      Console.println("[k.o.] rewriting refinement ")
	    for(val d <- tp.decls.elements) {
	      val otpe = d.tpe
	      d setInfo transformInfo(tp.symbol, d.tpe)
	      if(settings.debug.value) 
            Console.println("  "+otpe+"-->"+d.tpe)
	    }
	  }
	  tp
	}
  }

  /** A base class for transformers that maintain <code>outerParam</code>
   *  values for outer parameters of constructors.
   *  The class provides methods for referencing via outer.
   */
  abstract class OuterPathTransformer(unit: CompilationUnit) extends TypingTransformer(unit) {

    /** The directly enclosing outer parameter, if we are in a constructor */
    protected var outerParam: Symbol = NoSymbol

    /** The first outer selection from currently transformed tree.
     *  The result is typed but not positioned.
     */
    protected def outerValue: Tree =
      if (outerParam != NoSymbol) gen.mkAttributedIdent(outerParam)
      else outerSelect(gen.mkAttributedThis(currentClass))

    /** Select and apply outer accessor from 'base'
     *  The result is typed but not positioned.
     */
    private def outerSelect(base: Tree): Tree =
      localTyper.typed(Apply(Select(base, outerAccessor(base.tpe.symbol)), List()))

    /** The path
     *  <blockquote><pre>`base'.$outer$$C1 ... .$outer$$Cn</pre></blockquote>
     *  which refers to the outer instance of class <code>to</code> of
     *  value <code>base</code>. The result is typed but not positioned.
     *
     *  @param base ...
     *  @param from ...
     *  @param to   ...
     *  @return     ...
     */
    protected def outerPath(base: Tree, from: Symbol, to: Symbol): Tree = {
      //Console.println("outerPath from "+from+" to "+to+" at "+base+":"+base.tpe)
      //assert(base.tpe.widen.baseType(from.toInterface) != NoType, ""+base.tpe.widen+" "+from.toInterface)//DEBUG
      if (from == to || from.isImplClass && from.toInterface == to) base
      else outerPath(outerSelect(base), from.outerClass, to)
    }

    override def transform(tree: Tree): Tree = {
      val savedOuterParam = outerParam
      try {
        tree match {
          case Template(_, _) =>
            outerParam = NoSymbol
          case DefDef(_, _, _, vparamss, _, _) =>
            if (tree.symbol.isClassConstructor && isInner(tree.symbol.owner)) {
              outerParam = vparamss.head.head.symbol
              assert(outerParam.name == nme.OUTER)
            }
          case _ =>
        }
        super.transform(tree)
      } catch {//debug
        case ex: Throwable =>
          Console.println("exception when transforming " + tree)
          throw ex
      } finally {
        outerParam = savedOuterParam
      }
    }
  }

  /** <p>
   *    The phase performs the following transformations on terms:
   *  </p>
   *  <ol>
   *    <li> <!-- 1 -->
   *      <p>
   *        An class which is not an interface and is not static gets an outer
   *        accessor (@see outerDefs).
   *      </p>
   *      <p>
   *        1a. A class which is not a trait gets an outer field.
   *      </p>
   *    </li>
   *    <li> <!-- 2 -->
   *      A mixin which is not also an interface gets a mixin constructor
   *      (@see mixinConstructorDef)
   *    </li>
   *    <li> <!-- 3 -->
   *      Constructor bodies are augmented by calls to supermixin constructors
   *      (@see addMixinConstructorCalls)
   *    </li>
   *    <li> <!-- 4 -->
   *      A constructor of a non-trait inner class gets an outer parameter.
   *    </li>
   *    <li> <!-- 5 -->
   *      A reference <code>C.this</code> where <code>C</code> refers to an
   *      outer class is replaced by a selection
   *      <code>this.$outer$$C1</code> ... <code>.$outer$$Cn</code> (@see outerPath)
   *    </li>
   *    <li>
   *    </li>
   *    <li> <!-- 7 -->
   *      A call to a constructor Q.<init>(args) or Q.$init$(args) where Q != this and
   *      the constructor belongs to a non-static class is augmented by an outer argument.
   *      E.g. <code>Q.&lt;init&gt;(OUTER, args)</code> where <code>OUTER</code>
   *      is the qualifier corresponding to the singleton type <code>Q</code>.
   *    </li>
   *    <li>
   *      A call to a constructor <code>this.&lt;init&gt;(args)</code> in a
   *      secondary constructor is augmented to <code>this.&lt;init&gt;(OUTER, args)</code>
   *      where <code>OUTER</code> is the last parameter of the secondary constructor.
   *    </li>
   *    <li> <!-- 9 -->
   *      Remove <code>private</code> modifier from class members <code>M</code>
   *      that are accessed from an inner class.
   *    </li>
   *    <li> <!-- 10 -->
   *      Remove <code>protected</code> modifier from class members <code>M</code>
   *      that are accessed without a super qualifier accessed from an inner
   *      class or trait.
   *    </li>
   *    <li> <!-- 11 -->
   *      Remove <code>private</code> and <code>protected</code> modifiers
   *      from type symbols
   *    </li>
   *    <li> <!-- 12 -->
   *      Remove <code>private</code> modifiers from members of traits
   *    </li>
   *  </ol>
   *  <p>
   *    Note: The whole transform is run in phase <code>explicitOuter.next</code>.
   *  </p>
   */
  class ExplicitOuterTransformer(unit: CompilationUnit) extends OuterPathTransformer(unit) {

    /** The definition tree of the outer accessor of current class
     */
    def outerFieldDef: Tree = {
      val outerF = outerField(currentClass)
      ValDef(outerF, EmptyTree)
    }

    /** The definition tree of the outer accessor of current class
     */
    def outerAccessorDef: Tree = {
      val outerAcc = outerAccessor(currentClass)
      var rhs = if (outerAcc hasFlag DEFERRED) EmptyTree
                else Select(This(currentClass), outerField(currentClass))
      localTyper.typed {
        atPos(currentClass.pos) {
          DefDef(outerAcc, vparamss => rhs)
        }
      }
    }

    /** The definition tree of the outer accessor for class
     * <code>mixinClass</code>.
     *
     *  @param mixinClass The mixin class which defines the abstract outer
     *                    accessor which is implemented by the generated one.
     *  @pre mixinClass is an inner class
     */
    def mixinOuterAccessorDef(mixinClass: Symbol): Tree = {
      val outerAcc = outerAccessor(mixinClass).overridingSymbol(currentClass)
      if (outerAcc == NoSymbol)
        Console.println("cc " + currentClass + ":" + currentClass.info.decls +
                        " at " + phase)//debug
      assert(outerAcc != NoSymbol)
      val path = gen.mkAttributedQualifier(currentClass.thisType.baseType(mixinClass).prefix)
      val rhs = ExplicitOuterTransformer.this.transform(path)
      localTyper.typed {
        atPos(currentClass.pos) {
          DefDef(outerAcc, vparamss => rhs)
        }
      }
    }

    /** The main transformation method */
    override def transform(tree: Tree): Tree = {

      val sym = tree.symbol
      if ((sym ne null) && sym.isType) {//(9)
        if (sym hasFlag PRIVATE) sym setFlag notPRIVATE
        if (sym hasFlag PROTECTED) sym setFlag notPROTECTED
      }
      tree match {

        // <removeOption>

        case sm @ Apply(fn1, List(vlue)) // scala.Some(x:T) -> x  if T <: AnyRef
        if settings.Xkilloption.value
        && wasOptionRef(sm.tpe) 
        && ((fn1.symbol eq null)             // in patterns, symbol is null
	    || fn1.symbol.isClassConstructor) =>

	      val fn = transform(fn1)
	      if(settings.debug.value)
            Console.println("[k.o. (apply)]"+tree+" ==> "+vlue)
          if((fn.symbol eq null) && (vlue.symbol ne null/*isInstanceOf[Ident]*/)) {
	    val argtpe = getOptionArg(sm.tpe)
	    Typed(vlue, TypeTree().setType(argtpe)).setType(argtpe) // pattern matcher optimizes to null check
	  } else vlue

	case Ident(_) if settings.Xkilloption.value && (definitions.NoneClass eq tree.symbol)  => // only appears in actors/Channel, possibly bug
	      if(settings.debug.value)
		    Console.println("[k.o. (ident)]"+tree+" ==> null")
          atPos(tree.pos) { typer.typed { Literal(Constant(null)) }}
	
        case nne: Select                                // scala.None -> null
        if settings.Xkilloption.value && nne.symbol == definitions.NoneClass => // wasNone(nne.tpe) =>   

	      if(settings.debug.value)
		Console.println("[k.o. (select)]"+tree+" ==> null")
          atPos(tree.pos) { typer.typed { Literal(Constant(null)) }}
	  
        case Apply(Select(t, nme.isEmpty),List())       // t.isEmpty -> t eq null
        if settings.Xkilloption.value && wasOption(t.tpe)  =>
	      if(settings.debug.value)
		Console.println("[k.o. (isEmpty)]"+tree+" ==> "+t+" eq null")
          IsNull(t)

        case Apply(Select(t, nme.get),List())           // t.get -> t if T <: Option[AnyRef]
        if settings.Xkilloption.value && wasOptionRef(t.tpe) =>
	      if(settings.debug.value)
		Console.println("[k.o. (get)]"+tree+" ==> "+t)
          t

        case Apply(TypeApply(Select(t, nme.isInstanceOf_), List(tt)), _) if settings.Xkilloption.value && wasOptionRef(t.tpe) =>
          NotNull(t) 

        case Apply(ta@TypeApply(s@Select(t, nme.asInstanceOf_), List(tt)), List()) if settings.Xkilloption.value && wasOptionRef(t.tpe) => //&& definitions.isSomeType(tt.tpe) => 
          val tpe = tt.tpe
          copy.Apply(tree, copy.TypeApply(ta, transform(s), List(TypeTree(tpe))), List())
        case Select(t, n)           // methods ...
        if settings.Xkilloption.value && wasOptionRef(t.tpe) && n != nme.get =>
	  
          val s = transform(t)
          val tmp = currentOwner.newVariable(tree.pos, cunit.fresh.newName("opt")).setInfo(s.tpe) // careful: pos has special meaning
          val nt = atPos(tree.pos) { typer.typed {
            Block(List(ValDef(tmp,s)),
                  If(NotNull(Ident(tmp)), 
                     Apply(Select(New(TypeTree(definitions.someType(s.tpe))), nme.CONSTRUCTOR), List(Ident(tmp))),
                     gen.mkAttributedRef(definitions.NoneClass) )
            )
          }}
          val ntree = copy.Select(tree,nt,n)
          if(settings.debug.value)
            Console.println("[k.o. (select)]"+tree+" ==> "+ntree)
          ntree

        case Select(t, n)           // methods ...
        if settings.Xkilloption.value && wasOption(t.tpe) && n != nme.get && t.symbol!=null =>

          def ntpe = if(t.tpe.isInstanceOf[SingleType]) t.tpe.widen else t.tpe
          val otpe = atPhase(phase.prev){ntpe}
          val nt = atPos(tree.pos) { typer.typed {
            If(NotNull(t), 
               t,
               gen.mkAttributedRef(definitions.NoneClass))
          }}
          val ntree = copy.Select(tree,nt,n)
          if(settings.debug.value)
            Console.println("[k.o. (select2)]"+tree+" ==> "+ntree)
          ntree

        case _                                   // e.g. map.get("bar") -> fix type
        if settings.Xkilloption.value
        && wasOptionRef(tree.tpe) =>
          if(settings.debug.value) {
            Console.println("[k.o.] setting type of "+tree+" which is a "+tree.getClass)
            Console.println("  ... to "+getOptionArg(tree.tpe))
          }

          val argtpe = getOptionArg(tree.tpe)
          transform(tree.setType(argtpe)) // need to transform? super.transform?

        // </removeOption>

        case Template(parents, decls) =>
          val newDefs = new ListBuffer[Tree]
          atOwner(tree, currentOwner) {
            if (!(currentClass hasFlag INTERFACE) || (currentClass hasFlag lateINTERFACE)) {
              if (isInner(currentClass)) {
                if (!currentClass.isTrait) newDefs += outerFieldDef // (1a)
                newDefs += outerAccessorDef // (1)
              }
              if (!currentClass.isTrait)
                for (val mc <- currentClass.mixinClasses)
                  if (outerAccessor(mc) != NoSymbol)
                    newDefs += mixinOuterAccessorDef(mc)
            }
          }
          super.transform(
            copy.Template(tree, parents, if (newDefs.isEmpty) decls else decls ::: newDefs.toList)
          )
        case DefDef(mods, name, tparams, vparamss, tpt, rhs) =>
          if (sym.isClassConstructor) {
            rhs match {
              case Literal(_) =>
                // replace unit rhs () by empty block {()}
                val rhs1 = Block(List(), rhs) setPos rhs.pos setType rhs.tpe
                transform(copy.DefDef(tree, mods, name, tparams, vparamss, tpt, rhs1))
              case _ =>
                val clazz = sym.owner
                val vparamss1 =
                  if (isInner(clazz)) { // (4)
                    val outerParam =
                      sym.newValueParameter(sym.pos, nme.OUTER) setInfo outerField(clazz).info
                    ((ValDef(outerParam) setType NoType) :: vparamss.head) :: vparamss.tail
                  } else vparamss
                super.transform(copy.DefDef(tree, mods, name, tparams, vparamss1, tpt, rhs))
            }
          } else { //todo: see whether we can move this to transformInfo
            if (sym.owner.isTrait && (sym hasFlag (ACCESSOR | SUPERACCESSOR)))
              sym.makeNotPrivate(sym.owner); //(2)

            if(settings.Xkilloption.value) {
	      //Console.println("vparamss"+vparamss)
	      val nvparamss = vparamss.map { x => super.transformValDefs(x) /*x.map { y => transform(y) if(wasOptionRef(y.tpe)) y.setType(getOptionArg(y.tpe)) */}
	      //Console.println("nvparamss"+nvparamss)
              val ntpt = if(wasOptionRef(tpt.tpe)) TypeTree(getOptionArg(tpt.tpe)) else tpt

                 return super.transform(copy.DefDef(tree,mods,name,tparams,nvparamss, ntpt, rhs))
               }
            super.transform(tree)
          }

        case This(qual) =>
          if (sym == currentClass || (sym hasFlag MODULE) && sym.isStatic) tree
          else atPos(tree.pos)(outerPath(outerValue, currentClass.outerClass, sym)) // (5)

        case Select(qual, name) =>
          if (currentClass != sym.owner && currentClass != sym.moduleClass) // (3)
            sym.makeNotPrivate(sym.owner)
          val qsym = qual.tpe.widen.symbol
          if ((sym hasFlag PROTECTED) && //(4)
              (qsym.isTrait || !(qual.isInstanceOf[Super] || (qsym isSubClass currentClass))))
            sym setFlag notPROTECTED
          super.transform(tree)

        case Apply(sel @ Select(qual, name), args)
          if (name == nme.CONSTRUCTOR && isInner(sel.symbol.owner)) =>
            val outerVal = atPos(tree.pos) {
              if (qual.isInstanceOf[This]) { // it's a call between constructors of same class
                assert(outerParam != NoSymbol)
                outerValue
              } else {
                var pre = qual.tpe.prefix
                if (pre == NoPrefix) pre = sym.owner.outerClass.thisType
                gen.mkAttributedQualifier(pre)
              }
            }
            super.transform(copy.Apply(tree, sel, outerVal :: args))

        case Match(selector, cases) => // <----- transmatch hook
          val tid = if (settings.debug.value) {
            val q = unit.fresh.newName("tidmark")
            Console.println("transforming patmat with tidmark "+q+" ncases = "+cases.length)
            q
          } else null

          cases match { 
            //if ((cases.length > 1) && ...(cases(0)))
            //can't use treeInfo.isDefaultCase, because that diagnoses a Bind
            case CaseDef(Ident(nme.WILDCARD), EmptyTree, _)::xs if !xs.isEmpty => 
              // a hack to detect when explicit outer does not work correctly
              // still needed?
              assert(false,"transforming too much, " + tid)
            case _ =>
          }
            
          val nselector = transform(selector)
          assert(nselector.tpe =:= selector.tpe || settings.Xkilloption.value)
          val ncases = transformCaseDefs(cases)

          ExplicitOuter.this.resultType = tree.tpe
          //Console.println("TransMatcher currentOwner ="+currentOwner+")")
          //Console.println("TransMatcher selector.tpe ="+selector.tpe+")")
          //Console.println("TransMatcher resultType ="+resultType+")")

        val t_untyped = handlePattern(nselector, ncases, currentOwner, transform)
	try {
          //Console.println("t_untyped "+t_untyped.toString())
          val t = atPos(tree.pos) { localTyper.typed(t_untyped, resultType) }

          //t = transform(t)
          //val t         = atPos(tree.pos) { typed(t_untyped, resultType) }
          //val t         = atPos(tree.pos) { typed(t_untyped) }
          //Console.println("t typed "+t.toString())
          if (settings.debug.value)
            Console.println("finished translation of " + tid)
          t
	} catch {
	  case e => 
	    e.printStackTrace()
	    //treeBrowser.browse(Seq.single(unit).elements)
	    Console.println("[died while typechecking the translated pattern match:]")
	    Console.println(t_untyped)
	    System.exit(-1)
	  null
	}
    //<removeOption>
        case TypeTree() if settings.Xkilloption.value => 
          tree setType transformInfo(currentOwner, tree.tpe)
          tree
        case _ =>
	      val x = super.transform(tree)
	  
	      if(x.tpe eq null) x else {
            x setType transformInfo(currentOwner, x.tpe)
          }
      }
    }

    /** The transformation method for whole compilation units */
    override def transformUnit(unit: CompilationUnit): unit = {
      cunit = unit
      atPhase(phase.next) { super.transformUnit(unit) }
    }
  }
}


