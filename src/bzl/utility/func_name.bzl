"""func name"""

def func_name(f):
    """parse func name

    Args:
        f: func reference
    Returns:
        parsed func name
    """
    descr = str(f)
    fn_name_end_pos = descr.find(" from ") 
    if fn_name_end_pos == -1 or not descr.startswith("<function "):
        fail("frong function description: %s" % descr)

    return descr[10:fn_name_end_pos]
